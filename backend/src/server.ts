import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import { env } from './config/env.js';
import { apiRateLimiter } from './middleware/rateLimit.js';
import { adminRouter } from './routes/adminRoutes.js';
import { authRouter } from './routes/authRoutes.js';
import { catalogueRouter } from './routes/catalogueRoutes.js';
import { userRouter } from './routes/userRoutes.js';
import { telegramService } from './services/telegramService.js';

const app = express();

// Trust single reverse proxy hop (Cloud Run, Fly.io, Render, Nginx) for accurate rate limiting
app.set('trust proxy', 1);

app.use(
  helmet({
    crossOriginResourcePolicy: { policy: 'cross-origin' },
  })
);

// Strict CORS: native Android OkHttp/Media3 sends no Origin header; browsers must match CORS_ALLOWED_ORIGINS
app.use(
  cors({
    origin: (origin, callback) => {
      if (!origin) {
        callback(null, true);
        return;
      }
      if (env.corsOrigins.includes(origin)) {
        callback(null, true);
        return;
      }
      callback(new Error('Origin not allowed by CORS policy.'));
    },
    methods: ['GET', 'POST', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Authorization', 'Content-Type', 'Range', 'X-Axion-Client'],
    exposedHeaders: ['Content-Range', 'Accept-Ranges', 'Content-Length', 'Content-Type'],
    credentials: false,
  })
);

app.use(express.json({ limit: '256kb' }));
app.use(apiRateLimiter);

// Health & readiness probe (exposes zero secrets)
app.get('/health', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'axion-stream-backend',
    telegram_configured: telegramService.isConfigured(),
    admin_configured: env.isAdminConfigured,
    timestamp: new Date().toISOString(),
  });
});

// User Streaming Application APIs (100% compatible with Android AxionApiService.kt)
app.use('/api/auth', authRouter);
app.use('/api', catalogueRouter);
app.use('/api/user', userRouter);

// Isolated Admin Management APIs (separate Admin App only)
app.use('/api/admin', adminRouter);

// 404 fallback
app.use((_req, res) => {
  res.status(404).json({
    code: 'ROUTE_NOT_FOUND',
    message: 'Endpoint not found.',
  });
});

// Centralized error handler (never leaks stack traces in production)
app.use(
  (
    err: Error,
    _req: express.Request,
    res: express.Response,
    _next: express.NextFunction
  ) => {
    if (err.message.includes('CORS')) {
      res.status(403).json({ code: 'CORS_FORBIDDEN', message: 'Origin is not permitted.' });
      return;
    }
    console.error('Unhandled backend error:', err);
    res.status(500).json({
      code: 'INTERNAL_ERROR',
      message: env.NODE_ENV === 'production' ? 'Internal server error.' : err.message,
    });
  }
);

const server = app.listen(env.PORT, '0.0.0.0', () => {
  console.log(
    `[axion-stream-backend] listening on :${env.PORT} (${env.NODE_ENV}) | telegram=${telegramService.isConfigured()} | admin=${env.isAdminConfigured}`
  );
});

async function gracefulShutdown(signal: string) {
  console.log(`[axion-stream-backend] received ${signal}, shutting down...`);
  server.close(async () => {
    await telegramService.shutdown();
    process.exit(0);
  });
}

process.on('SIGINT', () => void gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => void gracefulShutdown('SIGTERM'));
