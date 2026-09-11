import { api, dayLabel, money, type Booking } from './api';

const POLL_EVERY_MS = 2500;
const GIVE_UP_AFTER_MS = 90_000;

export type ResultOptions = {
  days: string[];
  /** Called when the customer wants to try paying again after a failure. Omit to show a link home instead. */
  onRetry?: () => void;
};

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string, text?: string) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function row(label: string, value: string) {
  const div = el('div', 'ticket-row');
  div.append(el('dt', undefined, label), el('dd', undefined, value));
  return div;
}

/** Renders the state of a booking into `container`, replacing whatever was there. */
export function renderResult(container: HTMLElement, b: Booking, opts: ResultOptions) {
  container.replaceChildren();
  container.dataset.status = b.status;

  if (b.status === 'AWAITING_PAYMENT') {
    const box = el('div', 'waiting');
    box.setAttribute('role', 'status');
    box.append(el('span', 'waiting-dot'));
    const text = el('div');
    text.append(el('p', 'waiting-title', 'Waiting for the payment'));
    if (b.paymentMethod === 'MPESA') {
      text.append(
        el('p', undefined, `Check your phone. Enter your M-Pesa PIN to pay the ${money(b.deposit)} deposit.`),
        el('p', 'muted', "We'll confirm here the moment Safaricom tells us the money moved. Your slot is held for three minutes."),
      );
    } else {
      text.append(
        el('p', undefined, `Finish paying the ${money(b.deposit)} deposit on the card page.`),
        el('p', 'muted', "We'll confirm here once the card provider tells us it went through. Your slot is held for three minutes."),
      );
    }
    box.append(text);
    container.append(box);

    // DELETE BEFORE PRODUCTION — demo escape hatch, only visible while a payment is pending.
    const sim = el('p', 'simulate');
    const link = el('a', undefined, 'Demo: mark this as paid without a provider');
    link.href = '#';
    link.addEventListener('click', async (e) => {
      e.preventDefault();
      try {
        renderResult(container, await api.simulate(b.ref), opts);
      } catch (err: any) {
        link.replaceWith(el('span', 'error-text', err.message));
      }
    });
    sim.append(link);
    container.append(sim);
    return;
  }

  if (b.status === 'CONFIRMED') {
    const card = el('article', 'ticket');
    card.setAttribute('aria-live', 'polite');
    card.append(el('p', 'ticket-kicker', "You're booked"));
    const ref = el('p', 'ticket-ref', b.ref);
    ref.setAttribute('aria-label', `Booking reference ${b.ref.split('').join(' ')}`);
    card.append(ref);
    card.append(el('p', 'ticket-hint', 'Give this reference at the shop.'));

    const dl = el('dl', 'ticket-rows');
    dl.append(row('Cut', b.serviceName));
    dl.append(row('Day', dayLabel(b.date, opts.days)));
    dl.append(row('Time', b.slotTime));
    dl.append(row('Deposit paid', money(b.deposit)));
    if (b.balance == null) {
      dl.append(row('Balance', 'Price confirmed at the shop'));
    } else {
      dl.append(row('Balance due at the shop', money(b.balance)));
    }
    card.append(dl);
    if (b.receipt && b.receipt !== 'SIMULATED' && b.receipt !== 'M-PESA') {
      card.append(el('p', 'ticket-receipt', `Receipt ${b.receipt}`));
    }
    container.append(card);
    return;
  }

  if (b.status === 'FAILED' || b.status === 'EXPIRED') {
    const box = el('div', 'notice notice-bad');
    box.setAttribute('role', 'alert');
    box.append(el('p', 'notice-title', b.status === 'EXPIRED' ? 'We ran out of time' : "That payment didn't go through"));
    box.append(el('p', undefined, b.message ?? "The payment didn't happen."));
    box.append(el('p', 'muted', 'The slot was released and nothing was charged.'));
    if (opts.onRetry) {
      const btn = el('button', 'btn', 'Try paying again');
      btn.type = 'button';
      btn.addEventListener('click', opts.onRetry);
      box.append(btn);
    } else {
      const a = el('a', 'btn', 'Book again');
      a.href = '/';
      box.append(a);
    }
    container.append(box);
    return;
  }

  // NEW: nothing has been started yet.
  container.append(el('p', 'muted', 'No payment has been started for this booking.'));
}

/**
 * Polls until the booking leaves AWAITING_PAYMENT, calling `onUpdate` on every response.
 * Gives up after 90 seconds and calls `onGiveUp`; the booking may still settle server-side.
 */
export function pollUntilSettled(
  ref: string,
  onUpdate: (b: Booking) => void,
  onGiveUp: () => void,
  onError: (message: string) => void,
): { stop: () => void } {
  let stopped = false;
  const started = Date.now();

  const tick = async () => {
    if (stopped) return;
    try {
      const b = await api.get(ref);
      if (stopped) return;
      onUpdate(b);
      if (b.status !== 'AWAITING_PAYMENT') {
        stopped = true;
        return;
      }
    } catch (err: any) {
      if (err?.status === 404) {
        stopped = true;
        onError(err.message);
        return;
      }
      // transient — keep polling
    }
    if (Date.now() - started >= GIVE_UP_AFTER_MS) {
      stopped = true;
      onGiveUp();
      return;
    }
    setTimeout(tick, POLL_EVERY_MS);
  };

  setTimeout(tick, POLL_EVERY_MS);
  return { stop: () => { stopped = true; } };
}

/** Shown when polling gives up: the server may still settle, so offer a manual check. */
export function renderGaveUp(container: HTMLElement, ref: string, onCheckAgain: () => void) {
  container.replaceChildren();
  const box = el('div', 'notice');
  box.setAttribute('role', 'status');
  box.append(el('p', 'notice-title', "We haven't heard back yet"));
  box.append(el('p', undefined, `We've stopped checking automatically. Your reference is ${ref}. If you did pay, check again in a moment.`));
  const btn = el('button', 'btn', 'Check again');
  btn.type = 'button';
  btn.addEventListener('click', onCheckAgain);
  box.append(btn);
  container.append(box);
}
