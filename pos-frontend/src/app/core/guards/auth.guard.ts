import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { CompanyService } from '../services/company.service';
import { CompanyPermissionsService } from '../services/company-permissions.service';
import { catchError, map, of } from 'rxjs';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const companies = inject(CompanyService);
  const permissions = inject(CompanyPermissionsService);

  if (authService.isAuthenticated()) {
    const companyId = route.paramMap.get('id');
    if (companyId && state.url.startsWith('/company/')) {
      return companies.getMisPermisos(companyId).pipe(
        map((response) => {
          const subscription = response.suscripcion_activa;
          if (!subscription || !['activo', 'activa'].includes(subscription.estado.toLowerCase())) {
            return router.createUrlTree(['/my-companies'], { queryParams: { activar: companyId } });
          }
          permissions.savePermissions(response.permisos);
          permissions.saveActiveSubscription(subscription);
          const limits = subscription.plan?.limites;
          if (
            limits &&
            ((state.url.includes('/orders') && !limits.pedidos) ||
              (/\/reports\/(dynamic|qbe|analytics|prediction)/.test(state.url) &&
                !limits.predicciones) ||
              (state.url.includes('/reports/parameterized') && limits.codigo === 'gratuito'))
          ) {
            return router.createUrlTree(['/my-companies'], { queryParams: { ampliar: companyId } });
          }
          return true;
        }),
        catchError(() => of(router.createUrlTree(['/my-companies']))),
      );
    }
    return true;
  }

  return router.createUrlTree(['/login'], {
    queryParams: {
      returnUrl: state.url,
    },
  });
};
