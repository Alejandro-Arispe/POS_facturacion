import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CompanyWebSocketService } from './company-websocket.service';
import { AuthService } from './auth.service';

class TestSocket {
  static latest: TestSocket;
  onopen: any;
  onmessage: any;
  onclose: any;
  onerror: any;
  readonly send = vi.fn();
  readonly close = vi.fn();
  constructor(readonly url: string) {
    TestSocket.latest = this;
  }
}
describe('Autenticación del dashboard en vivo', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.stubGlobal('WebSocket', TestSocket);
  });
  afterEach(() => vi.unstubAllGlobals());
  function setup(token: string | null = 'sesion-de-prueba') {
    TestBed.configureTestingModule({
      providers: [
        CompanyWebSocketService,
        { provide: Router, useValue: { events: new Subject() } },
        { provide: AuthService, useValue: { getAccessToken: () => token } },
      ],
    });
    const service = TestBed.inject(CompanyWebSocketService);
    (service as any).connect('1');
    return { service, socket: TestSocket.latest };
  }
  it('envía la sesión en el primer mensaje y conserva la URL libre de credenciales', () => {
    const { socket } = setup();
    expect(socket.url).not.toContain('sesion-de-prueba');
    socket.onopen();
    expect(JSON.parse(socket.send.mock.calls[0][0])).toEqual({
      tipo: 'autenticar',
      token: 'sesion-de-prueba',
    });
  });
  it('retira el último dashboard al cerrar la conexión y nunca autentica sin sesión', () => {
    const { service, socket } = setup();
    socket.onmessage({ data: JSON.stringify({ tipo: 'dashboard', datos: [{}] }) });
    expect(service.messages$.value).not.toBeNull();
    socket.onclose({ code: 1008, reason: 'Revocado' });
    expect(service.messages$.value).toBeNull();
    (TestBed.inject(AuthService) as any).getAccessToken=() => null;
    (service as any).connect('2');const next=TestSocket.latest;next.onopen();
    expect(next.send).not.toHaveBeenCalled();expect(next.close).toHaveBeenCalled();
  });
});
