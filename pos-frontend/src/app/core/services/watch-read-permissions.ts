import { effect, inject, untracked } from '@angular/core';
import { PolicyRevisionService } from './policy-revision.service';

/** Discard displayed snapshots after a policy change; the next view/export must fetch again. */
export function watchReadPermissions(discard: () => void) {
  const permissions = inject(PolicyRevisionService);
  let previous: number | null = null;
  return effect(() => {
    const next = permissions.revision();
    if (previous !== null && previous !== next) untracked(discard);
    previous = next;
  });
}
