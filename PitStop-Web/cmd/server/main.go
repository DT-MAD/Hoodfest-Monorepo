// Command server runs the Pit Stop leaderboard API and display.
//
//	DATABASE_URL=postgres://... API_KEY=... ADMIN_KEY=... go run ./cmd/server
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"edu.dixietech.pitstop/internal/api"
	"edu.dixietech.pitstop/internal/config"
	"edu.dixietech.pitstop/internal/store"
)

func main() {
	log := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: levelFromEnv(),
	}))

	if err := run(log); err != nil {
		log.Error("server stopped", "error", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	// Signal handling is installed first so Ctrl-C works even while the
	// database connection is still being established.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	startupCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()

	db, err := store.OpenPostgres(startupCtx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer db.Close()
	log.Info("connected to Postgres and applied the schema")

	srv, err := api.New(cfg, db, log)
	if err != nil {
		return err
	}

	httpSrv := &http.Server{
		Addr:    cfg.Addr(),
		Handler: srv.Handler(),
		// No WriteTimeout: the leaderboard display holds an SSE stream open
		// for the length of the event, and a write deadline would sever it.
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	errs := make(chan error, 1)
	go func() {
		log.Info("Pit Stop is listening",
			"addr", httpSrv.Addr,
			"leaderboard", "http://localhost"+httpSrv.Addr+"/",
			"admin", "http://localhost"+httpSrv.Addr+"/admin")

		if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errs <- err
		}
	}()

	select {
	case err := <-errs:
		return err
	case <-ctx.Done():
		log.Info("shutting down")
	}

	shutdownCtx, cancelShutdown := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancelShutdown()

	if err := httpSrv.Shutdown(shutdownCtx); err != nil {
		return err
	}
	log.Info("stopped cleanly")
	return nil
}

// levelFromEnv lets the booth operator turn on debug logging without a rebuild.
func levelFromEnv() slog.Level {
	switch os.Getenv("LOG_LEVEL") {
	case "debug", "DEBUG":
		return slog.LevelDebug
	case "warn", "WARN":
		return slog.LevelWarn
	case "error", "ERROR":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
