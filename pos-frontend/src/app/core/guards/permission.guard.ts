import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';

import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../services/company-permissions.service';

export const permissionGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const permissionsService = inject(CompanyPermissionsService);
  const router = inject(Router);

  const requiredPermission = route.data['permission'] as string | string[] | undefined;
  if (!requiredPermission) {
    return true;
  }

  const permissions = Array.isArray(requiredPermission) ? requiredPermission : [requiredPermission];
  const hasAccess = permissions.some((perm) =>
    permissionsService.hasPermission(perm as CompanyPermissionCode),
  );

  if (hasAccess) {
    return true;
  }

  const companyId = route.paramMap.get('id') ?? route.parent?.paramMap.get('id') ?? '';
  if (companyId) {
    return router.createUrlTree(['/company', companyId, 'branches']);
  }
  return router.createUrlTree(['/my-companies']);
};
