---
primaryTarget: src/app/features/landing/plans/plans.html
relatedTargets:
  - src/app/shared/components/onboarding-steps/onboarding-steps.ts
  - src/app/features/auth/register/register.html
  - src/app/features/auth/checkout/checkout.ts
  - src/app/features/companies/pages/payment-result/payment-result.html
---

# Alta por pasos

Mantener el sistema visual blanco y azul de DESIGN.md, Tailwind y Lucide.
El indicador muestra cuatro pasos: Plan → Cuenta → Pago → Acceso.
La verificación continúa directamente a Stripe sin pedir una empresa ni presentar
una tarjeta de resumen o botón adicional de confirmación.

La empresa se registra después desde Mis empresas. El pago queda guardado a nombre
del usuario y se aplica una sola vez a la empresa que registre. El gratuito omite
Stripe. La pantalla de retorno solo presenta éxito cuando el servidor confirma
el estado pagado o la suscripción activa de una empresa anterior.

Conservar recuperación tras cancelar, errores de Stripe, verificación de correo,
formulario de empresa posterior, tamaños móviles y permisos del POS. La URL de
retorno utiliza el origen permitido del frontend que inició el flujo. Las rutas
anteriores de éxito siguen disponibles para sesiones ya creadas.

