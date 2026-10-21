import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environments } from '../../environments/environments';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly baseUrl: string = environments.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  get<TResponse>(endpoint: string): Observable<TResponse> {
    return this.http.get<TResponse>(this.buildUrl(endpoint));
  }

  post<TResponse, TBody>(endpoint: string, body: TBody): Observable<TResponse> {
    return this.http.post<TResponse>(this.buildUrl(endpoint), body);
  }

  put<TResponse, TBody>(endpoint: string, body: TBody): Observable<TResponse> {
    return this.http.put<TResponse>(this.buildUrl(endpoint), body);
  }

  patch<TResponse, TBody = any>(endpoint: string, body: TBody): Observable<TResponse> {
    return this.http.patch<TResponse>(this.buildUrl(endpoint), body);
  }


  delete<TResponse>(endpoint: string): Observable<TResponse> {
    return this.http.delete<TResponse>(this.buildUrl(endpoint));
  }

  private buildUrl(endpoint: string): string {
    const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;

    return `${this.baseUrl}${cleanEndpoint}`;
  }

  getBaseUrl(): string {
    return this.baseUrl;
  }
}
