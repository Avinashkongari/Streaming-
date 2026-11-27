/**
 * Parses a standard HTTP Range header ("bytes=start-end", "bytes=start-", or "bytes=-suffix").
 */
export function parseHttpRange(
  rangeHeader: string | undefined,
  totalSize: number
): { start: number; end: number; isPartial: boolean } | 'INVALID' {
  if (totalSize <= 0) return 'INVALID';

  if (!rangeHeader) {
    return {
      start: 0,
      end: Math.max(0, totalSize - 1),
      isPartial: false,
    };
  }

  const match = /^bytes=(\d*)-(\d*)$/i.exec(rangeHeader.trim());
  if (!match) return 'INVALID';

  const [, startStr, endStr] = match;
  let start: number;
  let end: number;

  if (!startStr && !endStr) {
    return 'INVALID';
  } else if (!startStr && endStr) {
    const suffixLength = Number.parseInt(endStr, 10);
    if (!Number.isFinite(suffixLength) || suffixLength <= 0) return 'INVALID';
    start = Math.max(0, totalSize - suffixLength);
    end = totalSize - 1;
  } else {
    start = Number.parseInt(startStr!, 10);
    end = endStr ? Number.parseInt(endStr, 10) : totalSize - 1;
  }

  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    start < 0 ||
    start >= totalSize ||
    end < start
  ) {
    return 'INVALID';
  }

  return {
    start,
    end: Math.min(end, totalSize - 1),
    isPartial: true,
  };
}
