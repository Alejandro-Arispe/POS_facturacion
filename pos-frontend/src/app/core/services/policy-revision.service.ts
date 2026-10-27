import { Injectable, signal } from '@angular/core';

/** Local epoch for discarding requests started under a previous policy or tenant. */
@Injectable({ providedIn: 'root' })
export class PolicyRevisionService {
  readonly revision = signal(0);
  private fingerprints = new Map<string, string>();

  update(part: string, fingerprint: string): void {
    if (this.fingerprints.get(part) === fingerprint) return;
    this.fingerprints.set(part, fingerprint);
    this.revision.update((value) => value + 1);
  }

  reset(): void {
    this.fingerprints.clear();
    this.revision.update((value) => value + 1);
  }
}
