import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService, RegisterRequest } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  protected readonly form = {
    nombreCompleto: '',
    email: '',
    fechaNacimiento: '',
    documento: '',
    genero: '',
    telefono: '',
    contrasena: '',
    confirmarContrasena: '',
  };

  protected codigo = '';
  protected emailParaVerificar = '';
  protected mostrarModalVerificacion = false;
  protected puedeIngresarCodigoVerificacion = false;
  protected cargandoRegistro = false;
  protected cargandoVerificacion = false;
  protected mensaje = '';
  protected error = '';
  protected errorVerificacion = '';
  protected mensajeVerificacion = '';

  constructor(
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router,
  ) {}

  protected registrar(event: SubmitEvent): void {
    event.preventDefault();
    this.error = '';
    this.mensaje = '';
    this.puedeIngresarCodigoVerificacion = false;

    if (this.form.contrasena !== this.form.confirmarContrasena) {
      this.error = 'Las contrasenas no coinciden.';
      return;
    }

    const payload: RegisterRequest = {
      email: this.form.email.trim(),
      contrasena: this.form.contrasena,
      nombre_completo: this.form.nombreCompleto.trim(),
      fecha_nacimiento: this.form.fechaNacimiento,
      genero: this.form.genero,
      telefono: this.form.telefono.trim(),
      documento: this.form.documento.trim(),
    };

    this.cargandoRegistro = true;

    this.authService.register(payload).subscribe({
      next: (response) => {
        this.cargandoRegistro = false;
        this.mensaje = response.mensaje ?? response.message ?? '';
        this.emailParaVerificar = response.email || payload.email;

        if (this.registroRequiereVerificacion(response)) {
          this.puedeIngresarCodigoVerificacion = true;
        } else {
          this.error = 'El registro respondio correctamente, pero no confirmo el envio del correo.';
        }

        this.cdr.detectChanges();
      },
      error: (error: HttpErrorResponse) => {
        this.cargandoRegistro = false;

        if (error.status === 200 && this.registroRequiereVerificacion(error.error)) {
          this.emailParaVerificar = payload.email;
          this.puedeIngresarCodigoVerificacion = true;
          this.cdr.detectChanges();
          return;
        }

        this.error =
          'No se pudo completar el registro. Revisa que el backend este disponible e intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  protected abrirIngresoCodigoVerificacion(): void {
    this.abrirModalVerificacion(this.emailParaVerificar || this.form.email.trim());
  }

  protected verificarCodigo(event: SubmitEvent): void {
    event.preventDefault();
    this.errorVerificacion = '';
    this.mensajeVerificacion = '';

    const codigo = this.codigo.trim();
    const email = this.emailParaVerificar.trim();

    if (!email) {
      this.errorVerificacion = 'Ingresa el correo de la cuenta.';
      return;
    }

    if (!codigo) {
      this.errorVerificacion = 'Ingresa el codigo de verificacion.';
      return;
    }

    this.cargandoVerificacion = true;

    this.authService.verifyCode({ email, codigo }).subscribe({
      next: (response) => {
        this.cargandoVerificacion = false;
        this.mensajeVerificacion = response.mensaje ?? 'Cuenta verificada correctamente.';
        this.cdr.detectChanges();

        setTimeout(() => {
          this.mostrarModalVerificacion = false;
          void this.router.navigate(['/']);
        }, 5000);
      },
      error: () => {
        this.cargandoVerificacion = false;
        this.errorVerificacion = 'No se pudo verificar el codigo. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  protected cerrarModalVerificacion(): void {
    this.mostrarModalVerificacion = false;
  }

  private abrirModalVerificacion(email: string): void {
    this.emailParaVerificar = email;
    this.codigo = '';
    this.errorVerificacion = '';
    this.mensajeVerificacion = '';
    this.mostrarModalVerificacion = true;
  }

  private registroRequiereVerificacion(response: unknown): boolean {
    if (this.valorEsVerdadero(response)) {
      return true;
    }

    const payload = response as {
      email_enviado?: unknown;
      emailEnviado?: unknown;
      registered?: unknown;
      data?: {
        email_enviado?: unknown;
        emailEnviado?: unknown;
        registered?: unknown;
      };
    };

    return (
      this.valorEsVerdadero(payload?.email_enviado) ||
      this.valorEsVerdadero(payload?.emailEnviado) ||
      this.valorEsVerdadero(payload?.registered) ||
      this.valorEsVerdadero(payload?.data?.email_enviado) ||
      this.valorEsVerdadero(payload?.data?.emailEnviado) ||
      this.valorEsVerdadero(payload?.data?.registered)
    );
  }

  private valorEsVerdadero(valor: unknown): boolean {
    if (typeof valor === 'string') {
      const valorNormalizado = valor.trim().toLowerCase();

      return valorNormalizado === 'true' || valorNormalizado === '1';
    }

    return valor === true || valor === 1;
  }
}
