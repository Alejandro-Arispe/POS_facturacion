import { Component, OnDestroy, OnInit, ViewChild, signal, inject } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { I18nService, SUPPORTED_LANGUAGES } from '../../../core/i18n/i18n.service';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { CompanyPermissionsService } from '../../../core/services/company-permissions.service';
import { FcmService } from '../../../core/notifications/fcm.service';
import { NotificationDetailModalComponent } from '../notification-center/notification-detail-modal/notification-detail-modal.component';
import { NotificationCenterComponent } from '../notification-center/notification-center.component';
import { NotificationToastComponent } from '../notification-center/notification-toast/notification-toast.component';
import { NotificationDetailModel } from '../notification-center/stock-alerts/stock-alerts';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [TranslatePipe, RouterLink, NotificationCenterComponent, NotificationToastComponent, NotificationDetailModalComponent],
  templateUrl: './navbar.html',
  styleUrls: ['./navbar.css'],
})
export class Navbar implements OnInit, OnDestroy {
  protected readonly i18n = inject(I18nService);
  protected readonly languages = SUPPORTED_LANGUAGES;
  protected readonly notifsOpen = signal(false);
  protected readonly unreadNotifications = signal(0);
  protected activeDetail: NotificationDetailModel | null = null;
  private notificationsEnabled = false;

  @ViewChild(NotificationCenterComponent) private notificationCenter?: NotificationCenterComponent;
  @ViewChild(NotificationToastComponent) private notificationToast?: NotificationToastComponent;

  private readonly stockNotificationListener = (event: Event) => {
    if (!this.canViewAlerts()) {
      return;
    }

    const customEvent = event as CustomEvent<any>;
    const item = this.notificationCenter?.handleIncoming(customEvent.detail);
    if (item) {
      this.notificationToast?.show(item);
    }
  };

  constructor(
    protected readonly authService: AuthService,
    private readonly router: Router,
    private readonly fcmService: FcmService,
    private readonly companyPermissionsService: CompanyPermissionsService,
  ) {}

  async ngOnInit(): Promise<void> {
    const companyId = this.getCompanyIdFromUrl();

    this.notificationsEnabled = this.authService.isAuthenticated() && companyId !== null;
    if (!this.notificationsEnabled) {
      return;
    }

    await this.fcmService.registerToken(undefined, companyId);
    window.addEventListener('stock-notification', this.stockNotificationListener);

    if (this.canViewAlerts()) {
      await this.loadUnreadNotifications();
    }
  }

  ngOnDestroy(): void {
    if (this.notificationsEnabled) {
      window.removeEventListener('stock-notification', this.stockNotificationListener);
    }
  }

  protected cerrarSesion(): void {
    this.authService.logout();
    void this.router.navigate(['/']);
  }

  protected toggleNotifs(): void {
    if (!this.canViewAlerts()) {
      this.notifsOpen.set(false);
      return;
    }

    this.notifsOpen.update(v => !v);
  }

  protected closeNotifs(): void {
    this.notifsOpen.set(false);
  }

  protected updateUnreadNotifications(count: number): void {
    if (!this.canViewAlerts()) {
      this.unreadNotifications.set(0);
      return;
    }

    this.unreadNotifications.set(count);
  }

  protected actualizarAlertaEmergente(item: any): void {
    if (!this.canViewAlerts()) {
      return;
    }

    this.notificationToast?.show(item);
  }

  protected abrirDetalleAlerta(item: any): void {
    if (!item || !this.canViewAlerts()) {
      return;
    }
    // If we received a canonical detail model (has fields), show it directly.
    if (item && typeof item === 'object' && Array.isArray(item.fields)) {
      this.activeDetail = item as NotificationDetailModel;
      return;
    }

    // Otherwise assume it's a NotificationViewModel; delegate to the notification center
    // so it can build the detail model, mark the notification as read, and emit the
    // canonical detailRequested event which will be handled by this same method.
    try {
      this.notificationCenter?.openDetail(item as any);
    } catch {
      // Fallback: if notificationCenter isn't available, set activeDetail if item
      this.activeDetail = item as any;
    }
  }

  protected cerrarDetalleAlerta(): void {
    this.activeDetail = null;
  }

  protected canViewAlerts(): boolean {
    return this.getCompanyIdFromUrl() !== null && this.companyPermissionsService.permissions().ALERTA_VER === true;
  }

  private async loadUnreadNotifications(): Promise<void> {
    const companyId = this.getCompanyIdFromUrl();

    if (!this.canViewAlerts() || companyId === null) {
      this.unreadNotifications.set(0);
      return;
    }

    try {
      const base = this.fcmService.getApiBase();
      const responses = await Promise.all([
        fetch(`${base}/notifications/history/empresas/${companyId}`),
      ]);

      const payloads = await Promise.all(
        responses
          .filter((response) => response.ok)
          .map((response) => response.json() as Promise<{ items?: Array<{ leido?: boolean }> }>),
      );

      const unreadCount = payloads
        .flatMap((data) => data.items || [])
        .filter((item) => !item.leido).length;
      this.unreadNotifications.set(unreadCount);
    } catch {
      this.unreadNotifications.set(0);
    }
  }

  private getCompanyIdFromUrl(): number | null {
    const match = this.router.url.match(/\/company\/([^/?#]+)/);
    const companyId = Number(match?.[1]);

    return Number.isFinite(companyId) ? companyId : null;
  }
}
