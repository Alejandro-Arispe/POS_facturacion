import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { PolicyRevisionService } from '../services/policy-revision.service';
import { environments } from '../../environments/environments';

export const policyRevisionInterceptor: HttpInterceptorFn = (request, next) => {
  const policy = inject(PolicyRevisionService);
  const revision = policy.revision();
  const path = request.url.startsWith(environments.apiBaseUrl)
    ? request.url.slice(environments.apiBaseUrl.length)
    : request.url;
  // Context responses establish the policy themselves. Auth/public onboarding is independent.
  const protectedResponse = /^\/(api|media)\//.test(path)
    && !/^\/api\/(auth|invitaciones|planes)(\/|\?|$)/.test(path)
    && !/\/mis-permisos(\?|$)/.test(path);

  return next(request).pipe(tap((event) => {
    if (protectedResponse && event instanceof HttpResponse && revision !== policy.revision()) {
      // A write may already have committed: never retry it automatically.
      throw new HttpErrorResponse({
        status: 409,
        url: request.url,
        error: { detail: 'El contexto de acceso cambió. Actualiza la vista antes de continuar.' },
      });
    }
  }));
};
