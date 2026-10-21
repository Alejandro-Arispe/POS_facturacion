import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { NotificationDetailModel } from '../stock-alerts/stock-alerts';

@Component({
  selector: 'app-notification-detail-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-detail-modal.component.html',
  styleUrls: ['./notification-detail-modal.component.css'],
})
export class NotificationDetailModalComponent {
  @Input() detail: NotificationDetailModel | null = null;
  @Output() readonly closeRequested = new EventEmitter<void>();

  close(): void {
    this.closeRequested.emit();
  }
}
