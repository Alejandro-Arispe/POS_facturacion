import { Injectable } from '@angular/core';

type SidebarGroupState = Record<string, boolean>;

@Injectable({ providedIn: 'root' })
export class SidebarStateService {
  private readonly storagePrefix = 'pos-frontend-web.sidebar-state';
  private readonly cache = new Map<string, SidebarGroupState>();

  getState(contextKey: string): SidebarGroupState {
    if (this.cache.has(contextKey)) {
      return { ...this.cache.get(contextKey) };
    }

    const state = this.readState(contextKey);
    this.cache.set(contextKey, state);
    return { ...state };
  }

  getGroupState(contextKey: string, groupLabel: string): boolean | undefined {
    const state = this.getState(contextKey);
    return state[groupLabel];
  }

  setGroupState(contextKey: string, groupLabel: string, isOpen: boolean): void {
    const state = this.getState(contextKey);
    state[groupLabel] = isOpen;
    this.cache.set(contextKey, state);
    this.writeState(contextKey, state);
  }

  ensureGroupState(contextKey: string, groupLabel: string, defaultState: boolean): void {
    const state = this.getState(contextKey);

    if (state[groupLabel] !== undefined) {
      return;
    }

    state[groupLabel] = defaultState;
    this.cache.set(contextKey, state);
    this.writeState(contextKey, state);
  }

  private readState(contextKey: string): SidebarGroupState {
    if (typeof sessionStorage === 'undefined') {
      return {};
    }

    try {
      const rawState = sessionStorage.getItem(this.getStorageKey(contextKey));
      return rawState ? (JSON.parse(rawState) as SidebarGroupState) : {};
    } catch {
      return {};
    }
  }

  private writeState(contextKey: string, state: SidebarGroupState): void {
    if (typeof sessionStorage === 'undefined') {
      return;
    }

    sessionStorage.setItem(this.getStorageKey(contextKey), JSON.stringify(state));
  }

  private getStorageKey(contextKey: string): string {
    return `${this.storagePrefix}:${contextKey}`;
  }
}