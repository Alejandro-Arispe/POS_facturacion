import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { ChequePaymentRequest } from '../../../core/services/cash-register.service';

export function isChequePaymentMethod(name: string): boolean {
  return name.trim().toUpperCase() === 'CHEQUE';
}

export function emptyChequePayment(): ChequePaymentRequest {
  return { banco: '', numero: '', fecha_emision: '', cobrado: false };
}

export function chequePaymentError(cheque: ChequePaymentRequest | undefined): string {
  if (!cheque?.banco.trim() || !cheque.numero.trim() || !cheque.fecha_emision) {
    return 'Ingresa el banco, el número y la fecha de emisión del cheque.';
  }
  return cheque.cobrado ? '' : 'Confirma que el cheque fue cobrado antes de registrar el pago.';
}

@Component({
  selector: 'app-cheque-payment-fields',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'group', 'aria-label': 'Datos del cheque' },
  template: `
    @let cheque = payment();
    <label class="form-group">
      <span class="label">Banco</span>
      <input class="input" type="text" maxlength="100" required
        [ngModelOptions]="standalone" [ngModel]="cheque.banco"
        (ngModelChange)="cheque.banco = $event; cheque.cobrado = false" />
    </label>
    <label class="form-group">
      <span class="label">Número de cheque</span>
      <input class="input" type="text" maxlength="50" required
        [ngModelOptions]="standalone" [ngModel]="cheque.numero"
        (ngModelChange)="cheque.numero = $event; cheque.cobrado = false" />
    </label>
    <label class="form-group">
      <span class="label">Fecha de emisión</span>
      <input class="input" type="date" required
        [ngModelOptions]="standalone" [ngModel]="cheque.fecha_emision"
        (ngModelChange)="cheque.fecha_emision = $event; cheque.cobrado = false" />
    </label>
    <label class="confirmation">
      <input type="checkbox" [ngModelOptions]="standalone" [(ngModel)]="cheque.cobrado" />
      Confirmo que el cheque ya fue cobrado.
    </label>
  `,
  styles: `
    :host {
      grid-column: 1 / -1;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 10rem), 1fr));
      gap: var(--space-md);
    }
    .confirmation {
      grid-column: 1 / -1;
      display: flex;
      align-items: center;
      gap: var(--space-sm);
    }
  `,
})
export class ChequePaymentFields {
  readonly payment = input.required<ChequePaymentRequest>();
  protected readonly standalone = { standalone: true };
}
