import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { BehaviorSubject } from 'rxjs';
import { environments } from '../../environments/environments';

@Injectable({
  providedIn: 'root',
})
export class CompanyWebSocketService {
  private readonly router = inject(Router);
  private socket: WebSocket | null = null;
  private currentCompanyId: string | null = null;
  readonly messages$ = new BehaviorSubject<any>(null);
  readonly connection$ = new BehaviorSubject<'idle' | 'connecting' | 'connected' | 'error'>('idle');

  reconnect(): void {
    if (this.currentCompanyId) this.connect(this.currentCompanyId);
  }

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.handleRouteEvent(event.urlAfterRedirects || event.url);
      });
  }

  private handleRouteEvent(url: string): void {
    // Matches "/company/:id" patterns
    const match = url.match(/\/company\/([^/]+)/);
    const companyId = match ? match[1] : null;

    if (companyId !== this.currentCompanyId) {
      this.messages$.next(null);
      this.currentCompanyId = companyId;
      if (companyId) {
        this.connect(companyId);
      } else {
        this.disconnect();
      }
    }
  }

  private connect(companyId: string): void {
    this.disconnect();
    this.connection$.next('connecting');

    const baseUrl = environments.apiBaseUrl;
    const wsProtocol = baseUrl.startsWith('https') ? 'wss' : 'ws';
    const host = baseUrl.replace(/^https?:\/\//, '').replace(/\/$/, '');
    const wsUrl = `${wsProtocol}://${host}/ws/administrador/${companyId}`;

    console.log(`Connecting to WebSocket: ${wsUrl}`);
    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        this.connection$.next('connected');
        console.log(`WebSocket connected to company ${companyId}`);
      };

      this.socket.onmessage = (event) => {
        console.log(`WebSocket message received for company ${companyId}:`, event.data);
        try {
          const parsed = JSON.parse(event.data);
          this.messages$.next(parsed);
        } catch (err) {
          console.error('Error parsing WebSocket message:', err);
        }
      };

      this.socket.onerror = (error) => {
        this.connection$.next('error');
        console.error(`WebSocket error for company ${companyId}:`, error);
      };

      this.socket.onclose = (event) => {
        this.connection$.next('error');
        console.log(
          `WebSocket closed for company ${companyId}. Code: ${event.code}, Reason: ${event.reason}`,
        );
      };
    } catch (err) {
      this.connection$.next('error');
      console.error(`Failed to initiate WebSocket connection for company ${companyId}:`, err);
    }
  }

  private disconnect(): void {
    if (this.socket) {
      this.socket.onopen = null;
      this.socket.onmessage = null;
      this.socket.onerror = null;
      this.socket.onclose = null;
      console.log(`Disconnecting WebSocket from company.`);
      this.socket.close();
      this.socket = null;
    }
    this.connection$.next('idle');
  }
}
