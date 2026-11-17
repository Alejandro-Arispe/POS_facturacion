import 'package:flutter/material.dart';

import '../../../app/router.dart';
import '../../../core/constants/app_palette.dart';
import '../models/credit_model.dart';
import '../services/profile_service.dart';

class CreditsPage extends StatefulWidget {
  final int companyId;
  final String companyName;
  final int clientId;

  const CreditsPage({
    super.key,
    required this.companyId,
    required this.companyName,
    required this.clientId,
  });

  @override
  State<CreditsPage> createState() => _CreditsPageState();
}

class _CreditsPageState extends State<CreditsPage> with SingleTickerProviderStateMixin {
  final _profileService = ProfileService();
  late TabController _tabController;
  bool _isLoading = true;
  String? _errorMessage;

  List<AccountReceivable> _credits = [];
  final List<AccountReceivable> _pendingCredits = [];
  final List<AccountReceivable> _overdueCredits = [];
  final List<AccountReceivable> _paidCredits = [];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadCredits();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadCredits() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      _credits = await _profileService.getClientCredits(widget.companyId, widget.clientId);
      _classifyCredits();
      setState(() {
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Error al cargar créditos: $e';
        _isLoading = false;
      });
    }
  }

  void _classifyCredits() {
    final now = DateTime.now();
    _pendingCredits.clear();
    _overdueCredits.clear();
    _paidCredits.clear();

    for (final credit in _credits) {
      final estadoUpper = credit.estado.trim().toUpperCase();
      
      if (credit.saldoPendiente <= 0 || estadoUpper == 'PAGADO' || estadoUpper == 'PAGADA' || estadoUpper == 'COMPLETO') {
        _paidCredits.add(credit);
      } else if (credit.fechaVencimiento.isBefore(now) || estadoUpper == 'VENCIDO' || estadoUpper == 'VENCIDA' || estadoUpper == 'MORA') {
        _overdueCredits.add(credit);
      } else {
        _pendingCredits.add(credit);
      }
    }

    // Sort by due date (closest first for pending/overdue, newest first for paid)
    _pendingCredits.sort((a, b) => a.fechaVencimiento.compareTo(b.fechaVencimiento));
    _overdueCredits.sort((a, b) => a.fechaVencimiento.compareTo(b.fechaVencimiento));
    _paidCredits.sort((a, b) => b.fechaInicio.compareTo(a.fechaInicio));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppPalette.bg,
      appBar: AppBar(
        title: Column(
          children: [
            const Text(
              'Mis Créditos',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            Text(
              widget.companyName,
              style: const TextStyle(fontSize: 12, color: AppPalette.textSoft),
            ),
          ],
        ),
        backgroundColor: Colors.transparent,
        elevation: 0,
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppPalette.text,
          unselectedLabelColor: AppPalette.textSoft,
          indicatorColor: AppPalette.primary,
          indicatorSize: TabBarIndicatorSize.tab,
          indicatorWeight: 3,
          tabs: [
            Tab(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Pendientes'),
                  if (_pendingCredits.isNotEmpty) ...[
                    const SizedBox(width: 4),
                    _buildBadgeCount(_pendingCredits.length, AppPalette.warning),
                  ],
                ],
              ),
            ),
            Tab(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Vencidos'),
                  if (_overdueCredits.isNotEmpty) ...[
                    const SizedBox(width: 4),
                    _buildBadgeCount(_overdueCredits.length, AppPalette.danger),
                  ],
                ],
              ),
            ),
            Tab(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Pagados'),
                  if (_paidCredits.isNotEmpty) ...[
                    const SizedBox(width: 4),
                    _buildBadgeCount(_paidCredits.length, AppPalette.success),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
      body: SafeArea(
        child: _isLoading
            ? const Center(
                child: CircularProgressIndicator(
                  color: AppPalette.primary,
                ),
              )
            : _errorMessage != null
                ? Center(
                    child: Card(
                      margin: const EdgeInsets.all(24),
                      color: AppPalette.surface,
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.error_outline, color: AppPalette.danger, size: 48),
                            const SizedBox(height: 16),
                            Text(
                              _errorMessage!,
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: 24),
                            ElevatedButton(
                              onPressed: _loadCredits,
                              child: const Text('Reintentar'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                : TabBarView(
                    controller: _tabController,
                    children: [
                      _buildCreditList(_pendingCredits, 'No tienes créditos pendientes de pago.', Icons.check_circle_outline, AppPalette.success),
                      _buildCreditList(_overdueCredits, '¡Excelente! No tienes créditos vencidos.', Icons.verified_outlined, AppPalette.success),
                      _buildCreditList(_paidCredits, 'Aún no has completado el pago de ningún crédito.', Icons.history_outlined, AppPalette.textSoft),
                    ],
                  ),
      ),
    );
  }

  Widget _buildBadgeCount(int count, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        count.toString(),
        style: const TextStyle(
          color: Colors.white,
          fontSize: 10,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildCreditList(List<AccountReceivable> list, String emptyMsg, IconData emptyIcon, Color iconColor) {
    Widget content;
    if (list.isEmpty) {
      content = SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(24),
        child: Container(
          alignment: Alignment.center,
          constraints: const BoxConstraints(minHeight: 300),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(emptyIcon, size: 64, color: iconColor.withValues(alpha: 0.8)),
              const SizedBox(height: 16),
              Text(
                emptyMsg,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AppPalette.textSoft,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      );
    } else {
      content = ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        itemCount: list.length,
        itemBuilder: (context, index) {
          final credit = list[index];
          return _buildCreditCard(credit);
        },
      );
    }

    return RefreshIndicator(
      onRefresh: _loadCredits,
      color: AppPalette.primary,
      backgroundColor: AppPalette.surface,
      child: content,
    );
  }

  Widget _buildCreditCard(AccountReceivable credit) {
    final now = DateTime.now();
    final isOverdue = credit.fechaVencimiento.isBefore(now) && credit.saldoPendiente > 0;
    final isPaid = credit.saldoPendiente <= 0;

    String dateLabel = 'Vence el:';
    String daysLabel = '';
    Color statusColor = AppPalette.warning;

    if (isPaid) {
      dateLabel = 'Pagado el:';
      statusColor = AppPalette.success;
      if (credit.pagosCredito.isNotEmpty) {
        // Find latest payment date
        final latestPay = credit.pagosCredito.reduce((curr, next) => curr.fechaPago.isAfter(next.fechaPago) ? curr : next);
        daysLabel = 'Pagado el ${_formatDate(latestPay.fechaPago)}';
      } else {
        daysLabel = 'Pagado';
      }
    } else if (isOverdue) {
      dateLabel = 'Venció el:';
      final diff = now.difference(credit.fechaVencimiento).inDays;
      daysLabel = 'Vencido hace $diff días';
      statusColor = AppPalette.danger;
    } else {
      final diff = credit.fechaVencimiento.difference(now).inDays;
      daysLabel = 'Faltan $diff días';
      statusColor = AppPalette.warning;
    }

    final formattedEmission = _formatDate(credit.fechaInicio);
    final formattedDue = _formatDate(credit.fechaVencimiento);

    return Card(
      color: AppPalette.surface,
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppPalette.border),
      ),
      elevation: 2,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () {
          Navigator.of(context).pushNamed(
            AppRouter.creditDetail,
            arguments: credit,
          );
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Crédito #${credit.idCxc}',
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                      color: AppPalette.text,
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusColor.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: statusColor.withValues(alpha: 0.4)),
                    ),
                    child: Text(
                      isPaid 
                          ? 'PAGADO' 
                          : isOverdue 
                              ? 'VENCIDO' 
                              : 'PENDIENTE',
                      style: TextStyle(
                        color: statusColor,
                        fontWeight: FontWeight.bold,
                        fontSize: 11,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Monto Crédito',
                        style: TextStyle(color: AppPalette.textSoft, fontSize: 12),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '\$${credit.montoCredito.toStringAsFixed(2)}',
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: AppPalette.text,
                        ),
                      ),
                    ],
                  ),
                  if (!isPaid) ...[
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        const Text(
                          'Saldo Pendiente',
                          style: TextStyle(color: AppPalette.textSoft, fontSize: 12),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '\$${credit.saldoPendiente.toStringAsFixed(2)}',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: AppPalette.danger,
                          ),
                        ),
                      ],
                    ),
                  ]
                ],
              ),
              const Divider(height: 24, color: AppPalette.border),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Emisión: $formattedEmission',
                        style: const TextStyle(color: AppPalette.textSoft, fontSize: 11),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '$dateLabel $formattedDue',
                        style: TextStyle(
                          color: isOverdue ? AppPalette.danger : AppPalette.textSoft,
                          fontSize: 11,
                          fontWeight: isOverdue ? FontWeight.bold : FontWeight.normal,
                        ),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      Text(
                        daysLabel,
                        style: TextStyle(
                          color: statusColor,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(Icons.chevron_right, color: AppPalette.textSoft),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    return '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
  }
}
