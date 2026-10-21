import { Injectable } from '@angular/core';
// actualizateeeee

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'pos-theme';

  initializeTheme(): void {
    const savedTheme = localStorage.getItem(this.THEME_KEY);
    if (savedTheme === 'oscuro') {
      this.setTheme('oscuro');
    } else {
      this.setTheme('claro');
    }
  }

  setTheme(tema: string): void {
    localStorage.setItem(this.THEME_KEY, tema);
    if (tema === 'oscuro') {
      document.body.classList.add('dark-theme');
      document.documentElement.classList.add('dark-theme');
    } else {
      document.body.classList.remove('dark-theme');
      document.documentElement.classList.remove('dark-theme');
    }
  }

  getTheme(): string {
    return localStorage.getItem(this.THEME_KEY) || 'claro';
  }
}
