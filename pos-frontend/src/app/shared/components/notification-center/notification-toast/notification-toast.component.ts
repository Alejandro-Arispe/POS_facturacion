import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, Output } from '@angular/core';

import { NotificationViewModel } from '../stock-alerts/stock-alerts';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-toast.component.html',
  styleUrls: ['./notification-toast.component.css'],
})
export class NotificationToastComponent implements OnDestroy {
  notification: NotificationViewModel | null = null;

  @Output() readonly detailRequested = new EventEmitter<NotificationViewModel>();
  @Output() readonly closed = new EventEmitter<void>();

  private timeoutId: ReturnType<typeof setTimeout> | null = null;

  show(notification: NotificationViewModel): void {
    this.clearTimeout();
    this.notification = notification;
    this.timeoutId = setTimeout(() => this.close(), 6000);
  }

  openDetail(): void {
    if (!this.notification) {
      return;
    }

    const current = this.notification;
    this.close();
    this.detailRequested.emit(current);
  }

  close(): void {
    this.clearTimeout();
    if (this.notification) {
      this.notification = null;
      this.closed.emit();
    }
  }

  ngOnDestroy(): void {
    this.clearTimeout();
  }

  private clearTimeout(): void {
    if (this.timeoutId !== null) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
  }
}
