import { Component, signal, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CompanyWebSocketService } from './core/services/company-websocket.service';
import { ThemeService } from './core/services/theme.service';
import { PreferencesService } from './core/services/preferences.service';
import { AssistantWidget } from './shared/components/assistant/assistant-widget';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AssistantWidget],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly wsService = inject(CompanyWebSocketService);
  private readonly themeService = inject(ThemeService);
  private readonly preferencesService = inject(PreferencesService);
  protected readonly title = signal('pos-frontend-web');

  constructor() {
    this.themeService.initializeTheme();
    this.preferencesService.initialize();
  }
}
