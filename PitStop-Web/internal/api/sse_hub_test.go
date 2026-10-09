package api

import (
	"io"
	"log/slog"
	"sync"
	"testing"
)

func testHub() *hub {
	return newHub(slog.New(slog.NewTextHandler(io.Discard, nil)))
}

func TestHubBroadcastsToEverySubscriber(t *testing.T) {
	h := testHub()

	a, closeA := h.subscribe()
	b, closeB := h.subscribe()
	defer closeA()
	defer closeB()

	if h.subscriberCount() != 2 {
		t.Fatalf("subscriberCount = %d, want 2", h.subscriberCount())
	}

	h.broadcast([]byte(`{"hello":"world"}`))

	for i, ch := range []<-chan []byte{a, b} {
		select {
		case got := <-ch:
			if string(got) != `{"hello":"world"}` {
				t.Errorf("subscriber %d got %q", i, got)
			}
		default:
			t.Errorf("subscriber %d received nothing", i)
		}
	}
}

func TestHubUnsubscribeStopsDelivery(t *testing.T) {
	h := testHub()
	ch, cancel := h.subscribe()

	cancel()
	if h.subscriberCount() != 0 {
		t.Errorf("subscriberCount = %d after cancel, want 0", h.subscriberCount())
	}

	// The channel is closed, so a receive returns immediately with !ok. The
	// SSE handler relies on this to shut its loop down.
	if _, open := <-ch; open {
		t.Error("the subscriber channel is still open after unsubscribing")
	}

	// Broadcasting after everyone has left must not panic on a closed channel.
	h.broadcast([]byte(`{}`))
}

func TestHubCancelIsIdempotent(t *testing.T) {
	h := testHub()
	_, cancel := h.subscribe()

	cancel()
	cancel() // A double close would panic if unsubscribe were not guarded.

	if h.subscriberCount() != 0 {
		t.Errorf("subscriberCount = %d, want 0", h.subscriberCount())
	}
}

// TestHubDropsSubscribersThatFallBehind is the property that keeps a wedged
// browser from blocking a score submission.
func TestHubDropsSubscribersThatFallBehind(t *testing.T) {
	h := testHub()
	_, cancel := h.subscribe()
	defer cancel()

	// Never drain the channel. Once the buffer fills, the next broadcast must
	// drop the subscriber rather than block.
	for i := 0; i < clientBuffer+5; i++ {
		h.broadcast([]byte(`{"tick":1}`))
	}

	if h.subscriberCount() != 0 {
		t.Errorf("subscriberCount = %d, want the stalled subscriber dropped", h.subscriberCount())
	}
}

func TestHubIsSafeUnderConcurrency(t *testing.T) {
	h := testHub()

	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			ch, cancel := h.subscribe()
			go func() {
				for range ch {
				}
			}()
			h.broadcast([]byte(`{"n":1}`))
			cancel()
		}()
	}
	wg.Wait()

	if h.subscriberCount() != 0 {
		t.Errorf("subscriberCount = %d, want 0 once everyone has left", h.subscriberCount())
	}
}
