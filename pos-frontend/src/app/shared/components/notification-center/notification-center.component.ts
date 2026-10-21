import { Component, EventEmitter, NgZone, OnDestroy, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FcmService } from '../../../core/notifications/fcm.service';
import { ApiService } from '../../../core/services/api.service';
import {
  buildIncomingStockNotification,
  buildStockDetailModel,
  fetchStockAlertHistory,
  findLatestNewUnreadStockAlert,
  markStockAlertAsRead,
  NotificationDetailModel,
  NotificationViewModel,
  normalizeStockNotification,
  shouldHandleStockAlertEvent,
} from './stock-alerts/stock-alerts';

@Component({
  selector: 'app-notification-center',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-center.component.html',
  styleUrls: ['./notification-center.component.css']
})
export class NotificationCenterComponent implements OnInit, OnDestroy {
  items: NotificationViewModel[] = [];
  apiBase = '';
  @Output() closeRequested = new EventEmitter<void>();
  @Output() unreadCountChange = new EventEmitter<number>();
  @Output() incomingNotification = new EventEmitter<NotificationViewModel>();
  @Output() detailRequested = new EventEmitter<NotificationDetailModel>();
  private readonly knownNotificationIds = new Set<string>();
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly fcmService: FcmService,
    private readonly ngZone: NgZone,
    private readonly apiService: ApiService,
  ) {}

  async ngOnInit() {
    this.apiBase = this.apiService.getBaseUrl();
    await this.load(true);
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('message', (ev: any) => {
        this.ngZone.run(() => {
          if (shouldHandleStockAlertEvent(ev.data?.type)) {
            this.handleIncoming(ev.data.payload);
          }
        });
      });
    }
    this.startPolling();
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  async load(initialLoad = false) {
    const previousIds = new Set(this.knownNotificationIds);
    const idsEmpresa = await this.fcmService.obtenerEmpresasContexto();
    const historyItems = await fetchStockAlertHistory(this.apiBase, idsEmpresa);

    const nextItems = historyItems
      .map((it: any) => normalizeStockNotification(it))
      .sort((left: any, right: any) => right.sortAt.getTime() - left.sortAt.getTime());

    this.items = nextItems;
    this.knownNotificationIds.clear();
    for (const item of this.items) {
      this.knownNotificationIds.add(String(item.id));
    }

    this.emitUnreadCount();

    if (!initialLoad) {
      const latestNewUnreadStock = findLatestNewUnreadStockAlert(this.items, previousIds);

      if (latestNewUnreadStock) {
        this.incomingNotification.emit(latestNewUnreadStock);
      }
    }
  }

  async markRead(item: NotificationViewModel) {
    await markStockAlertAsRead(this.apiBase, item.id);
    item.leido = true;
    this.emitUnreadCount();
  }

  handleIncoming(payload: any): NotificationViewModel {
    return this.ngZone.run(() => {
      const item = buildIncomingStockNotification(payload);

      this.items.unshift(item);
      this.items.sort((left: any, right: any) => right.sortAt.getTime() - left.sortAt.getTime());
      this.knownNotificationIds.add(String(item.id));
      this.emitUnreadCount();
      this.incomingNotification.emit(item);
      return item;
    });
  }

  openDetail(item: NotificationViewModel) {
    const detail = buildStockDetailModel(item);
    this.detailRequested.emit(detail);
    if (!item.leido) this.markRead(item).catch(() => {});
  }

  requestClose(): void {
    this.closeRequested.emit();
  }

  private emitUnreadCount() {
    this.unreadCountChange.emit(this.items.filter((item) => !item.leido).length);
  }

  private startPolling(): void {
    if (this.pollHandle !== null) {
      return;
    }

    this.pollHandle = setInterval(() => {
      this.ngZone.run(() => {
        void this.load(false);
      });
    }, 15000);
  }
}
