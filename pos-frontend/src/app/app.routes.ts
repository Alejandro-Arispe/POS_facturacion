import { Routes } from '@angular/router';
import { EditCompany } from './features/companies/pages/edit-company/edit-company';
import { MyCompanies } from './features/companies/pages/my-companies/my-companies';
import { PaymentResultComponent } from './features/companies/pages/payment-result/payment-result';
import { RegisterCompany } from './features/companies/pages/register-company/register-company';
import { Branches } from './features/companies/sections/branches/pages/branches/branches';
import { EditBranch } from './features/companies/sections/branches/pages/edit-branch/edit-branch';
import { ViewBranch } from './features/companies/sections/branches/pages/view-branch/view-branch';
import { CashRegister } from './features/companies/sections/branches/sections/cash_register/cash_register';
import { CloseCashRegister } from './features/companies/sections/branches/sections/close_cash_register/close_cash_register';
import { Staff } from './features/companies/sections/users/staff/staff';
import { Rols } from './features/companies/sections/users/rols/rols';
import { OrdersInbox } from './features/companies/sections/orders/orders-inbox/orders-inbox';

import { Inventario } from './features/companies/sections/branches/sections/inventario/inventario';
import { OpenCashRegister } from './features/companies/sections/branches/sections/open_cash_register/open_cash_register';
import { Sales } from './features/companies/sections/branches/sections/sales/sales';
import { CreditCollections } from './features/companies/sections/branches/sections/credit_collections/credit_collections';
import { DynamicReports } from './features/companies/sections/reports/dynamic/dynamic';
import { ParameterizedReports } from './features/companies/sections/reports/parameterized/parameterized';
import { StaticReports } from './features/companies/sections/reports/static/static';
import { SalesPrediction } from './features/companies/sections/reports/prediction/prediction';
import { Dashboard } from './features/companies/sections/dashboard/dashboard';
import { CategoriasPanel } from './features/companies/sections/products/pages/categories/my-categories/my-categories';
import { EditCategory } from './features/companies/sections/products/pages/categories/edit-category/edit-category';
import { EditProduct } from './features/companies/sections/products/pages/catalog/edit-product/edit-product';
import { ProductosPanel } from './features/companies/sections/products/pages/catalog/my-catalog/my-catalog';
import { ViewCategory } from './features/companies/sections/products/pages/categories/view-category/view-category';
import { ViewProduct } from './features/companies/sections/products/pages/catalog/view-product/view-product';
import { CategoriasClientes } from './features/companies/sections/clients/pages/categories/my-categories/my-categories';
import { EditClientCategory } from './features/companies/sections/clients/pages/categories/edit-category/edit-category';
import { EditClient } from './features/companies/sections/clients/pages/edit-client';
import { ClientesCatalogo } from './features/companies/sections/clients/pages/catalog/my-clients/my-clients';
import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { HomePage } from './features/landing/home_page/home_page';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { superAdminGuard } from './core/guards/superadmin.guard';
import { SuperAdminPanel } from './features/superadmin/superadmin-panel';
import { AuditLog } from './features/companies/sections/audit/audit-log';
import { QbeReport } from './features/companies/sections/reports/qbe/qbe';
import { AnalyticsDashboard } from './features/companies/sections/reports/analytics/analytics';
import { Settings } from './features/companies/pages/settings/settings';

export const routes: Routes = [
  {
    path: '',
    component: HomePage,
  },
  {
    path: 'register',
    component: Register,
  },
  {
    path: 'login',
    component: Login,
  },
  {
    path: 'my-companies',
    component: MyCompanies,
    canActivate: [authGuard],
  },
  {
    path: 'payment/success',
    component: PaymentResultComponent,
    canActivate: [authGuard],
  },
  {
    path: 'payment/cancel',
    component: PaymentResultComponent,
    canActivate: [authGuard],
  },
  {
    path: 'register-company',
    component: RegisterCompany,
    canActivate: [authGuard],
  },
  {
    path: 'edit-company/:idEmpresa',
    component: EditCompany,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branches',
    component: Branches,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/view-branch',
    component: ViewBranch,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/edit-branch',
    component: EditBranch,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/users/staff',
    component: Staff,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'USUARIO_VER' },
  },
  {
    path: 'company/:id/users/rols',
    component: Rols,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'ROL_VER' },
  },
  {
    path: 'company/:id/branch/:branchId/cash-register',
    component: CashRegister,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/cash-register/:cashRegisterId/open-cash-register',
    component: OpenCashRegister,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/cash-register/:cashRegisterId/close-cash-register',
    component: CloseCashRegister,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/cash-register/:cashRegisterId/sales',
    component: Sales,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/cash-register/:cashRegisterId/credit-collections',
    component: CreditCollections,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/inventario',
    component: Inventario,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/branch/:branchId/orders',
    component: OrdersInbox,
    canActivate: [authGuard],
  },
  {
    path: 'superadmin',
    component: SuperAdminPanel,
    canActivate: [authGuard, superAdminGuard],
  },
  {
    path: 'company/:id/audit-log',
    component: AuditLog,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'BITACORA_VER' },
  },
  {
    path: 'company/:id/dashboard',
    component: Dashboard,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'DASHBOARD_VER' },
  },
  {
    path: 'company/:id/reports/static',
    component: StaticReports,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'REPORTE_GENERAR' },
  },
  {
    path: 'company/:id/reports/parameterized',
    component: ParameterizedReports,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'REPORTE_GENERAR' },
  },
  {
    path: 'company/:id/reports/analytics',
    component: AnalyticsDashboard,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'DASHBOARD_VER' },
  },
  {
    path: 'company/:id/reports/qbe',
    component: QbeReport,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'REPORTE_GENERAR' },
  },
  {
    path: 'company/:id/reports/dynamic',
    component: DynamicReports,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'REPORTE_GENERAR' },
  },
  {
    path: 'company/:id/reports/prediction',
    component: SalesPrediction,
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'REPORTE_GENERAR' },
  },
  {
    path: 'company/:id/reports/invoices',
    loadComponent: () =>
      import('./features/companies/sections/reports/invoices/invoices').then(
        (component) => component.InvoicesReport,
      ),
    canActivate: [authGuard, permissionGuard],
    data: { permission: 'REPORTE_GENERAR' },
  },
  {
    path: 'company/:id/products/categories',
    component: CategoriasPanel,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/products',
    component: ProductosPanel,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/clients',
    component: ClientesCatalogo,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/clients/categories',
    component: CategoriasClientes,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/clients/categories/:categoryId/edit-category',
    component: EditClientCategory,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/clients/:clientId/edit-client',
    component: EditClient,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/product/:productId/view-product',
    component: ViewProduct,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/product/:productId/edit-product',
    component: EditProduct,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/category/:categoryId/view-category',
    component: ViewCategory,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/category/:categoryId/edit-category',
    component: EditCategory,
    canActivate: [authGuard],
  },
  {
    path: 'company/:id/orders',
    loadComponent: () =>
      import('./features/companies/sections/orders/orders-inbox/orders-inbox').then(
        (m) => m.OrdersInbox
      ),
    canActivate: [authGuard],
  },
  {
    path: 'administrator/settings',
    component: Settings,
    canActivate: [authGuard],
  },
  {
    path: 'companie',
    redirectTo: 'my-companies',
    pathMatch: 'full',
  },
  {
    path: 'administrator/my-companies',
    redirectTo: 'my-companies',
    pathMatch: 'full',
  },
  {
    path: 'administrator/register-company',
    redirectTo: 'register-company',
    pathMatch: 'full',
  },
  {
    path: 'administrator/payment/success',
    redirectTo: 'payment/success',
    pathMatch: 'full',
  },
  {
    path: 'administrator/payment/cancel',
    redirectTo: 'payment/cancel',
    pathMatch: 'full',
  },
];
