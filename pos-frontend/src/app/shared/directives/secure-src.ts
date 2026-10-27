import { Directive, ElementRef, DestroyRef, Input, effect, inject, signal } from '@angular/core';
import { ApiService } from '../../core/services/api.service';
import { CompanyPermissionsService } from '../../core/services/company-permissions.service';
import { environments } from '../../environments/environments';

@Directive({ selector: 'img[secureSrc]' })
export class SecureSrc {
  private readonly element = inject(ElementRef<HTMLImageElement>);
  private readonly api = inject(ApiService);
  private readonly permissions = inject(CompanyPermissionsService);
  private readonly path = signal('');
  @Input() set secureSrc(value: string | null | undefined) {
    this.path.set(value ?? '');
  }
  constructor() {
    effect((cleanup) => {
      const image = this.element.nativeElement;
      image.removeAttribute('src');
      if (!this.permissions.hasField('product', 'read', 'imagen')) return;
      const base = environments.apiBaseUrl.replace(/\/$/, '');
      const value = this.path();
      const endpoint = value.startsWith(base + '/') ? value.substring(base.length) : value;
      if (!/^\/media\/products\/[a-zA-Z0-9_.-]+$/.test(endpoint)) return;
      let url: string | null = null;
      const request = this.api.getBlob(endpoint).subscribe({
        next: (blob) => {
          url = URL.createObjectURL(blob);
          image.src = url;
        },
        error: () => image.removeAttribute('src'),
      });
      cleanup(() => {
        request.unsubscribe();
        if (url) URL.revokeObjectURL(url);
        image.removeAttribute('src');
      });
    });
  }
}
