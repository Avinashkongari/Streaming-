import bigInt from 'big-integer';
import { Api, TelegramClient } from 'telegram';
import { StringSession } from 'telegram/sessions/index.js';
import { env } from '../config/env.js';

export interface TelegramVideoMetadata {
  messageId: number;
  channelId: string;
  fileName: string | null;
  mimeType: string;
  fileSizeBytes: number;
  durationSeconds: number;
  width: number | null;
  height: number | null;
  hasThumbnail: boolean;
  caption: string | null;
  dateIso: string | null;
}

interface CachedThumbnail {
  buffer: Buffer;
  contentType: string;
  expiresAt: number;
}

interface ResolvedVideoMessage {
  message: Api.Message;
  document: Api.Document;
  metadata: TelegramVideoMetadata;
}

const THUMB_CACHE_MAX_ITEMS = 60;
const THUMB_CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour
const MTPROTO_ALIGN_BYTES = 4096;

/**
 * Server-side Telegram MTProto service (GramJS).
 *
 * SECURITY & STREAMING GUARANTEES:
 * - Credentials (API_ID, API_HASH, SESSION_STRING, CHANNEL_ID) stay strictly in server memory/env.
 * - Supports private channels and videos up to ~200 MB+ (overcoming Bot API's 20 MB getFile limit).
 * - Streams arbitrary HTTP byte ranges directly chunk-by-chunk with zero permanent video disk storage.
 */
class TelegramStreamService {
  private client: TelegramClient | null = null;
  private connectPromise: Promise<TelegramClient> | null = null;
  private entityCache = new Map<string, { entity: Api.TypeInputPeer; expiresAt: number }>();
  private thumbnailCache = new Map<string, CachedThumbnail>();

  isConfigured(): boolean {
    return env.isTelegramConfigured;
  }

  private async getClient(): Promise<TelegramClient> {
    if (!env.isTelegramConfigured || !env.TELEGRAM_API_ID) {
      throw new TelegramUnavailableError(
        'Telegram MTProto integration is not configured on this server.'
      );
    }

    if (this.client && this.client.connected) {
      return this.client;
    }

    if (!this.connectPromise) {
      this.connectPromise = (async () => {
        const session = new StringSession(env.TELEGRAM_SESSION_STRING);
        const client = new TelegramClient(
          session,
          env.TELEGRAM_API_ID!,
          env.TELEGRAM_API_HASH,
          {
            connectionRetries: 5,
            useWSS: false,
          }
        );
        await client.connect();
        const authorized = await client.checkAuthorization();
        if (!authorized) {
          await client.disconnect();
          throw new TelegramUnavailableError(
            'Telegram session string is invalid or revoked. Re-generate TELEGRAM_SESSION_STRING.'
          );
        }
        this.client = client;
        return client;
      })().finally(() => {
        this.connectPromise = null;
      });
    }

    return this.connectPromise;
  }

  private async resolveChannelEntity(
    client: TelegramClient,
    channelId: string
  ): Promise<Api.TypeInputPeer> {
    const key = channelId.trim();
    const cached = this.entityCache.get(key);
    if (cached && cached.expiresAt > Date.now()) {
      return cached.entity;
    }

    const numeric = /^-?\d+$/.test(key) ? bigInt(key) : key;
    const entity = await client.getInputEntity(numeric);
    this.entityCache.set(key, {
      entity,
      expiresAt: Date.now() + 10 * 60 * 1000,
    });
    return entity;
  }

  private extractDocumentMetadata(
    channelId: string,
    message: Api.Message
  ): ResolvedVideoMessage | null {
    const media = message.media;
    if (!(media instanceof Api.MessageMediaDocument)) return null;
    const doc = media.document;
    if (!(doc instanceof Api.Document)) return null;

    const mimeType = doc.mimeType || 'video/mp4';
    const isVideoOrOctet =
      mimeType.startsWith('video/') ||
      mimeType === 'application/octet-stream' ||
      doc.attributes.some((attr) => attr instanceof Api.DocumentAttributeVideo);

    if (!isVideoOrOctet) return null;

    let durationSeconds = 0;
    let width: number | null = null;
    let height: number | null = null;
    let fileName: string | null = null;

    for (const attr of doc.attributes) {
      if (attr instanceof Api.DocumentAttributeVideo) {
        durationSeconds = Math.max(0, Math.round(attr.duration || 0));
        width = attr.w || null;
        height = attr.h || null;
      } else if (attr instanceof Api.DocumentAttributeFilename) {
        fileName = attr.fileName || null;
      }
    }

    const fileSizeBytes = Number(doc.size.toString());
    const hasThumbnail = Array.isArray(doc.thumbs) && doc.thumbs.length > 0;

    return {
      message,
      document: doc,
      metadata: {
        messageId: message.id,
        channelId,
        fileName,
        mimeType: mimeType.startsWith('video/') ? mimeType : 'video/mp4',
        fileSizeBytes,
        durationSeconds,
        width,
        height,
        hasThumbnail,
        caption: message.message?.trim() || null,
        dateIso: message.date ? new Date(message.date * 1000).toISOString() : null,
      },
    };
  }

  async resolveVideoMessage(
    channelId: string,
    messageId: number
  ): Promise<ResolvedVideoMessage> {
    const client = await this.getClient();
    const entity = await this.resolveChannelEntity(client, channelId);
    const messages = await client.getMessages(entity, { ids: [messageId] });
    const msg = messages[0];

    if (!msg || !(msg instanceof Api.Message)) {
      throw new TelegramMediaNotFoundError(
        `Video message #${messageId} was not found in the private channel.`
      );
    }

    const resolved = this.extractDocumentMetadata(channelId, msg);
    if (!resolved) {
      throw new TelegramMediaNotFoundError(
        `Message #${messageId} does not contain a streamable video document.`
      );
    }

    if (resolved.metadata.fileSizeBytes > env.MAX_VIDEO_SIZE_BYTES) {
      throw new TelegramMediaSizeLimitError(
        `Video exceeds the configured server size ceiling (${Math.round(env.MAX_VIDEO_SIZE_BYTES / (1024 * 1024))} MB).`
      );
    }

    return resolved;
  }

  /**
   * Automatically retrieves the embedded Telegram video thumbnail (JPEG) without downloading the video.
   */
  async getVideoThumbnail(
    channelId: string,
    messageId: number
  ): Promise<{ buffer: Buffer; contentType: string } | null> {
    const cacheKey = `${channelId}:${messageId}`;
    const cached = this.thumbnailCache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      return { buffer: cached.buffer, contentType: cached.contentType };
    }

    const client = await this.getClient();
    const { message, document } = await this.resolveVideoMessage(channelId, messageId);
    const thumbs = document.thumbs;
    if (!thumbs || thumbs.length === 0) {
      return null;
    }

    // Pick the highest-resolution standard PhotoSize thumbnail
    const preferredThumb =
      thumbs.find((t) => t instanceof Api.PhotoSize && (t.type === 'm' || t.type === 'x' || t.type === 'y')) ??
      thumbs.find((t) => t instanceof Api.PhotoSize) ??
      thumbs[thumbs.length - 1];

    const result = await client.downloadMedia(message, {
      thumb: preferredThumb,
    });

    if (!result || !Buffer.isBuffer(result) || result.length === 0) {
      return null;
    }

    // Evict oldest entry when cache reaches capacity
    if (this.thumbnailCache.size >= THUMB_CACHE_MAX_ITEMS) {
      const oldestKey = this.thumbnailCache.keys().next().value;
      if (oldestKey) this.thumbnailCache.delete(oldestKey);
    }

    const entry: CachedThumbnail = {
      buffer: result,
      contentType: 'image/jpeg',
      expiresAt: Date.now() + THUMB_CACHE_TTL_MS,
    };
    this.thumbnailCache.set(cacheKey, entry);

    return { buffer: entry.buffer, contentType: entry.contentType };
  }

  /**
   * Streams an exact inclusive byte range [start, end] from Telegram MTProto
   * using 4 KB-aligned offsets and yielding chunks immediately to the HTTP response.
   */
  async *streamVideoByteRange(
    document: Api.Document,
    start: number,
    end: number,
    abortSignal: AbortSignal
  ): AsyncGenerator<Buffer, void, void> {
    const client = await this.getClient();
    const totalNeeded = end - start + 1;
    if (totalNeeded <= 0) return;

    const alignedOffset = Math.floor(start / MTPROTO_ALIGN_BYTES) * MTPROTO_ALIGN_BYTES;
    let skipRemaining = start - alignedOffset;
    let bytesRemaining = totalNeeded;

    const fileLocation = new Api.InputDocumentFileLocation({
      id: document.id,
      accessHash: document.accessHash,
      fileReference: document.fileReference,
      thumbSize: '',
    });

    // Align request chunk size to a multiple of 4096 bytes
    const rawChunk = Math.min(
      env.STREAM_CHUNK_SIZE_BYTES,
      Math.max(MTPROTO_ALIGN_BYTES, Math.ceil((bytesRemaining + skipRemaining) / MTPROTO_ALIGN_BYTES) * MTPROTO_ALIGN_BYTES)
    );
    const requestSize = Math.max(
      MTPROTO_ALIGN_BYTES,
      Math.floor(rawChunk / MTPROTO_ALIGN_BYTES) * MTPROTO_ALIGN_BYTES
    );

    const iterator = client.iterDownload({
      file: fileLocation,
      offset: bigInt(alignedOffset),
      requestSize,
      dcId: document.dcId,
      fileSize: document.size,
    });

    for await (const rawChunkData of iterator) {
      if (abortSignal.aborted || bytesRemaining <= 0) {
        break;
      }

      let chunk = Buffer.isBuffer(rawChunkData)
        ? rawChunkData
        : Buffer.from(rawChunkData);

      if (skipRemaining > 0) {
        if (chunk.length <= skipRemaining) {
          skipRemaining -= chunk.length;
          continue;
        }
        chunk = chunk.subarray(skipRemaining);
        skipRemaining = 0;
      }

      if (chunk.length > bytesRemaining) {
        chunk = chunk.subarray(0, bytesRemaining);
      }

      if (chunk.length > 0) {
        bytesRemaining -= chunk.length;
        yield chunk;
      }

      if (bytesRemaining <= 0) {
        break;
      }
    }
  }

  /**
   * Lists recent video messages from the configured private Telegram channel (Admin only).
   */
  async listChannelVideos(
    channelId: string = env.TELEGRAM_CHANNEL_ID,
    limit: number = 30
  ): Promise<TelegramVideoMetadata[]> {
    const client = await this.getClient();
    const entity = await this.resolveChannelEntity(client, channelId);
    const safeLimit = Math.min(Math.max(limit, 1), 100);
    const messages = await client.getMessages(entity, { limit: safeLimit });

    const videos: TelegramVideoMetadata[] = [];
    for (const msg of messages) {
      if (!(msg instanceof Api.Message)) continue;
      const resolved = this.extractDocumentMetadata(channelId, msg);
      if (resolved) {
        videos.push(resolved.metadata);
      }
    }
    return videos;
  }

  async shutdown(): Promise<void> {
    if (this.client) {
      await this.client.disconnect().catch(() => {});
      this.client = null;
    }
  }
}

export class TelegramUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TelegramUnavailableError';
  }
}

export class TelegramMediaNotFoundError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TelegramMediaNotFoundError';
  }
}

export class TelegramMediaSizeLimitError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TelegramMediaSizeLimitError';
  }
}

export const telegramService = new TelegramStreamService();
