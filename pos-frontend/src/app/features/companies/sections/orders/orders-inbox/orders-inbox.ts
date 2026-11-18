import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { OrderService, Order } from '../../../../../core/services/order.service';
import { CashRegisterService } from '../../../../../core/services/cash-register.service';

@Component({
  selector: 'app-orders-inbox',
  imports: [CommonModule],
  templateUrl: './orders-inbox.html',
  styleUrl: './orders-inbox.css',
  standalone: true
})
export class OrdersInbox implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private cashRegisterService = inject(CashRegisterService);
  private readonly cdr = inject(ChangeDetectorRef);

  companyId!: number;
  branchId: number | null = null;
  orders: Order[] = [];
  filteredOrders: Order[] = [];
  
  isLoading = true;
  errorMessage = '';

  selectedOrder: Order | null = null;
  isModalOpen = false;

  currentFilter = 'todos';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    const bId = this.route.snapshot.paramMap.get('branchId');
    
    if (id) {
      this.companyId = parseInt(id, 10);
      if (bId) {
        this.branchId = parseInt(bId, 10);
      }
      this.loadOrders();
    } else {
      this.isLoading = false;
      this.errorMessage = 'No se encontró la empresa.';
      this.cdr.detectChanges();
    }
  }

  loadOrders(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.cdr.detectChanges();
    const branchToFilter = this.branchId ? this.branchId : undefined;
    this.orderService.getCompanyOrders(this.companyId, branchToFilter, this.currentFilter).subscribe({
      next: (data) => {
        const list = Array.isArray(data) ? data : [];
        // Sort by newest first
        this.orders = [...list].sort(
          (a, b) => new Date(b.fecha_creacion).getTime() - new Date(a.fecha_creacion).getTime(),
        );
        this.applyFilter();
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.errorMessage = 'Error al cargar pedidos';
        this.isLoading = false;
        this.cdr.detectChanges();
        console.error(err);
      }
    });
  }

  setFilter(filter: string): void {
    this.currentFilter = filter;
    this.loadOrders();
  }

  applyFilter(): void {
    this.filteredOrders = [...this.orders];
  }

  viewDetails(order: Order): void {
    this.selectedOrder = order;
    this.isModalOpen = true;
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.isModalOpen = false;
    this.selectedOrder = null;
    this.cdr.detectChanges();
  }

  changeStatus(orderId: number, status: string): void {
    this.orderService.updateOrderStatus(this.companyId, orderId, status).subscribe({
      next: (updatedOrder) => {
        const index = this.orders.findIndex(o => o.id_pedido === orderId);
        if (index !== -1) {
          this.orders[index] = updatedOrder;
          this.applyFilter();
        }
        if (this.selectedOrder?.id_pedido === orderId) {
            this.selectedOrder = updatedOrder;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error actualizando estado', err);
        alert('Error al actualizar el estado del pedido');
      }
    });
  }

  cargarEnPOS(order: Order): void {
    // Desde /company/:id/orders no hay branchId en la ruta: usar la sucursal del pedido.
    const branchId = this.branchId ?? order.id_sucursal;
    if (!branchId) {
      alert('Este pedido no tiene sucursal asignada; no se puede cargar al POS.');
      return;
    }

    // 1. Obtener cajas de esta sucursal
    this.cashRegisterService.getCajasSucursal(this.companyId.toString(), branchId.toString()).subscribe({
      next: (res) => {
        const list = Array.isArray(res) ? res : (res as any).cajas || (res as any).items || [];
        // 2. Buscar sesion activa en cualquier caja de la sucursal asignada a este usuario
        const activeBox = list.find((c: any) => c.sesion_activa && c.sesion_activa.es_usuario_actual);
        
        if (activeBox && activeBox.sesion_activa) {
          // Caja abierta por este cajero en esta sucursal -> Redirigir al POS
          const sessionId = activeBox.sesion_activa.id_caja_sesion;
          this.router.navigate(
            [`/company/${this.companyId}/branch/${branchId}/cash-register/${activeBox.id_caja}`],
            {
              queryParams: {
                sessionId: sessionId,
                section: 'sales',
                pedido_id: order.id_pedido
              }
            }
          );
        } else {
          // Sin caja abierta
          alert('Debes abrir una caja en Sucursal #' + branchId + ' antes de cargar este pedido en el POS.');
        }
      },
      error: (err) => {
        console.error('Error verificando cajas', err);
        alert('Error al verificar estado de caja de la sucursal.');
      }
    });
  }
}

