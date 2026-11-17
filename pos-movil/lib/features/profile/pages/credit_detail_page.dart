import 'package:flutter/material.dart';

import '../../../core/constants/app_palette.dart';
import '../models/credit_model.dart';

class CreditDetailPage extends StatelessWidget {
  final AccountReceivable credit;

  const CreditDetailPage({
    super.key,
    required this.credit,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final isPaid = credit.saldoPendiente <= 0;
    final isOverdue = credit.fechaVencimiento.isBefore(now) && credit.saldoPendiente > 0;

    Color statusColor = AppPalette.warning;
    String statusText = 'PENDIENTE';
    if (isPaid) {
      statusColor = AppPalette.success;
      statusText = 'PAGADO';
    } else if (isOverdue) {
      statusColor = AppPalette.danger;
      statusText = 'VENCIDO';
    }

    final double totalPaid = credit.montoCredito - credit.saldoPendiente;

    return Scaffold(
      backgroundColor: AppPalette.bg,
      appBar: AppBar(
        title: Text('Detalle de Crédito #${credit.idCxc}'),
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Credit Overview Card
              _buildOverviewCard(context, statusText, statusColor, totalPaid),
              const SizedBox(height: 20),

              // 2. Purchased Items Section
              _buildItemsSection(),
              const SizedBox(height: 20),

              // 3. Payment History Section
              _buildPaymentsSection(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildOverviewCard(BuildContext context, String statusText, Color statusColor, double totalPaid) {
    return Card(
      color: AppPalette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 4,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Row(
                  children: [
                    Icon(Icons.info_outline, color: AppPalette.textSoft),
                    SizedBox(width: 8),
                    Text(
                      'Resumen',
                      style: TextStyle(
                        color: AppPalette.text,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: statusColor.withValues(alpha: 0.4)),
                  ),
                  child: Text(
                    statusText,
                    style: TextStyle(
                      color: statusColor,
                      fontWeight: FontWeight.bold,
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(height: 24, color: AppPalette.border),
            
            // Financial Breakdown
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _buildFinancialColumn('Crédito Original', credit.montoCredito, AppPalette.text, 16),
                _buildFinancialColumn('Total Abonado', totalPaid, AppPalette.success, 16),
                _buildFinancialColumn('Saldo Pendiente', credit.saldoPendiente, AppPalette.danger, 18),
              ],
            ),
            
            const Divider(height: 32, color: AppPalette.border),
            
            // Dates details
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _buildDateDetail('Fecha Emisión', credit.fechaInicio),
                _buildDateDetail('Fecha Vencimiento', credit.fechaVencimiento),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFinancialColumn(String label, double amount, Color amountColor, double amountSize) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Text(
          label,
          style: const TextStyle(color: AppPalette.textSoft, fontSize: 11, fontWeight: FontWeight.w500),
        ),
        const SizedBox(height: 4),
        Text(
          '\$${amount.toStringAsFixed(2)}',
          style: TextStyle(
            color: amountColor,
            fontSize: amountSize,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }

  Widget _buildDateDetail(String label, DateTime date) {
    final formatted = '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: AppPalette.textSoft, fontSize: 11),
        ),
        const SizedBox(height: 2),
        Text(
          formatted,
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppPalette.text),
        ),
      ],
    );
  }

  Widget _buildItemsSection() {
    final detalles = credit.venta.detalles;

    return Card(
      color: AppPalette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.shopping_bag_outlined, color: AppPalette.textSoft),
                const SizedBox(width: 8),
                Text(
                  'Desglose de Compra (Venta #${credit.idVenta})',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: AppPalette.text,
                  ),
                ),
              ],
            ),
            const Divider(height: 24, color: AppPalette.border),
            
            // Product items list
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: detalles.length,
              separatorBuilder: (context, index) => const Divider(height: 16, color: AppPalette.surface2),
              itemBuilder: (context, index) {
                final item = detalles[index];
                final productName = item.producto?.nombre ?? 'Producto desconocido';
                final barcode = item.producto?.codigoBarra;
                final unit = item.producto?.unidadMedida ?? 'unid';

                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            productName,
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                              color: AppPalette.text,
                            ),
                          ),
                          if (barcode != null && barcode.isNotEmpty) ...[
                            const SizedBox(height: 2),
                            Text(
                              'EAN: $barcode',
                              style: const TextStyle(color: AppPalette.textSoft, fontSize: 11),
                            ),
                          ],
                          const SizedBox(height: 4),
                          Text(
                            '${item.cantidad} $unit x \$${item.precioUnitario.toStringAsFixed(2)}',
                            style: const TextStyle(color: AppPalette.textSoft, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '\$${item.subtotal.toStringAsFixed(2)}',
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                            color: AppPalette.text,
                          ),
                        ),
                        if (item.descuento > 0) ...[
                          const SizedBox(height: 2),
                          Text(
                            '- \$${item.descuento.toStringAsFixed(2)}',
                            style: const TextStyle(
                              color: AppPalette.danger,
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                );
              },
            ),
            
            const Divider(height: 24, color: AppPalette.border),
            
            // Sale total summary
            _buildSummaryRow('Subtotal', credit.venta.subtotal),
            const SizedBox(height: 6),
            if (credit.venta.descuentoTotal > 0) ...[
              _buildSummaryRow(
                'Descuento Total', 
                -credit.venta.descuentoTotal, 
                valueColor: AppPalette.danger
              ),
              const SizedBox(height: 6),
            ],
            _buildSummaryRow(
              'Total de la Venta', 
              credit.venta.total, 
              isBold: true, 
              fontSize: 16
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSummaryRow(String label, double amount, {bool isBold = false, double fontSize = 13, Color? valueColor}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            color: isBold ? AppPalette.text : AppPalette.textSoft,
            fontWeight: isBold ? FontWeight.bold : FontWeight.w500,
            fontSize: fontSize,
          ),
        ),
        Text(
          '\$${amount.toStringAsFixed(2)}',
          style: TextStyle(
            color: valueColor ?? AppPalette.text,
            fontWeight: isBold ? FontWeight.bold : FontWeight.bold,
            fontSize: fontSize,
          ),
        ),
      ],
    );
  }

  Widget _buildPaymentsSection() {
    final pagos = credit.pagosCredito;

    return Card(
      color: AppPalette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.history, color: AppPalette.textSoft),
                const SizedBox(width: 8),
                Text(
                  'Historial de Abonos (${pagos.length})',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: AppPalette.text,
                  ),
                ),
              ],
            ),
            const Divider(height: 24, color: AppPalette.border),

            if (pagos.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(
                  child: Text(
                    'No se han registrado abonos a este crédito.',
                    style: TextStyle(
                      color: AppPalette.textSoft,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ),
              )
            else
              ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: pagos.length,
                separatorBuilder: (context, index) => const Divider(height: 16, color: AppPalette.surface2),
                itemBuilder: (context, index) {
                  final pago = pagos[index];
                  final formattedDate = '${pago.fechaPago.day.toString().padLeft(2, '0')}/${pago.fechaPago.month.toString().padLeft(2, '0')}/${pago.fechaPago.year} ${pago.fechaPago.hour.toString().padLeft(2, '0')}:${pago.fechaPago.minute.toString().padLeft(2, '0')}';
                  final method = pago.metodoPago?.nombre ?? 'Efectivo';

                  return Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Abono #${pago.idPagoCredito}',
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                              color: AppPalette.text,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            formattedDate,
                            style: const TextStyle(color: AppPalette.textSoft, fontSize: 11),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Método: $method',
                            style: const TextStyle(color: AppPalette.textSoft, fontSize: 11),
                          ),
                        ],
                      ),
                      Text(
                        '+ \$${pago.montoPagado.toStringAsFixed(2)}',
                        style: const TextStyle(
                          color: AppPalette.success,
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  );
                },
              ),
          ],
        ),
      ),
    );
  }
}
