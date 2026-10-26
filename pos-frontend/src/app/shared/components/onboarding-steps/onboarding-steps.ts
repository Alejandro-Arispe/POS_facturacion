import { Component, input } from '@angular/core';

@Component({
  selector: 'app-onboarding-steps',
  template: ` <nav aria-label="Progreso del registro" class="steps">
    <ol>
      @for (label of labels; track label; let i = $index) {
        <li
          [class.done]="i + 1 < step()"
          [class.current]="i + 1 === step()"
          [attr.aria-current]="i + 1 === step() ? 'step' : null"
        >
          <span class="number" aria-hidden="true">{{ i + 1 < step() ? '✓' : i + 1 }}</span>
          <span>{{ label }}</span>
          <span class="sr-only">{{
            i + 1 < step() ? ', completado' : i + 1 === step() ? ', en curso' : ', pendiente'
          }}</span>
        </li>
      }
    </ol>
  </nav>`,
  styles: `
    :host {
      display: block;
      width: 100%;
    }
    ol {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      list-style: none;
      padding: 0;
      margin: 0;
    }
    li {
      position: relative;
      display: grid;
      justify-items: center;
      gap: 10px;
      color: var(--color-text-muted);
      font-size: 12px;
      text-align: center;
    }
    li:not(:last-child)::after {
      content: '';
      position: absolute;
      height: 1px;
      background: var(--color-border);
      top: 16px;
      left: calc(50% + 23px);
      width: calc(100% - 46px);
    }
    li.done::after {
      background: var(--pos-brand);
    }
    .number {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border: 1px solid var(--color-border);
      border-radius: 50%;
      background: var(--color-surface-solid);
      font-weight: 700;
    }
    .current {
      color: var(--color-text);
      font-weight: 700;
    }
    .current .number,
    .done .number {
      background: var(--pos-brand);
      border-color: var(--pos-brand);
      color: var(--color-surface-solid);
    }
    @media (max-width: 480px) {
      li {
        font-size: 10px;
        gap: 8px;
      }
      .number {
        width: 30px;
        height: 30px;
      }
      li:not(:last-child)::after {
        top: 14px;
        left: calc(50% + 19px);
        width: calc(100% - 38px);
      }
    }
  `,
})
export class OnboardingSteps {
  step = input.required<number>();
  protected readonly labels = ['Plan', 'Cuenta', 'Pago', 'Acceso'];
}
