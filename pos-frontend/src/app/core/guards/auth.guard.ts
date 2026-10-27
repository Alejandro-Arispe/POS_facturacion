import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { CompanyService } from '../services/company.service';
import { CompanyPermissionsService } from '../services/company-permissions.service';
import { catchError, map, of } from 'rxjs';
import { checkRoutePermission } from './permission.guard';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const companies = inject(CompanyService);
  const permissions = inject(CompanyPermissionsService);

  if (authService.isAuthenticated()) {
    const companyId =
      route.paramMap.get('id') ??
      route.paramMap.get('idEmpresa') ??
      route.queryParamMap?.get('companyId');
    if (companyId) {
      permissions.clearPermissions();
      return companies.getMisPermisos(companyId, route.paramMap.get('branchId')).pipe(
        map((response) => {
          const subscription = response.suscripcion_activa;
          if (!subscription || !['activo', 'activa'].includes(subscription.estado.toLowerCase())) {
            return router.createUrlTree(['/my-companies'], { queryParams: { activar: companyId } });
          }
          permissions.savePermissions(response.permisos);
          permissions.saveActiveSubscription(subscription);
          permissions.watchContext(companyId, route.paramMap.get('branchId'));
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
          return checkRoutePermission(route, permissions, router);
        }),
        catchError(() => {
          permissions.clearPermissions();
          return of(router.createUrlTree(['/my-companies']));
        }),
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
