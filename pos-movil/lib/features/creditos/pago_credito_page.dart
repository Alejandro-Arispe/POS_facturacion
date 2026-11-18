import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_service.dart';
import '../../core/constants/app_palette.dart';
import '../catalogo/producto_model.dart';
import '../profile/models/credit_model.dart';

/// Informar un pago de crédito (POST /api/creditos/me/{idCxc}/pagar).
/// Devuelve el [ReportedPayment] creado al cerrar, o null si se cancela.
class PagoCreditoPage extends StatefulWidget {
  final AccountReceivable credit;

  const PagoCreditoPage({super.key, required this.credit});

  @override
  State<PagoCreditoPage> createState() => _PagoCreditoPageState();
}

class _PagoCreditoPageState extends State<PagoCreditoPage> {
  // Métodos que el backend acepta para pagos informados desde la app (el efectivo se paga en caja).
  static const _metodosPermitidos = {'QR', 'TARJETA', 'TRANSFERENCIA'};

  final ApiService _api = ApiService();
  final _formKey = GlobalKey<FormState>();
  final _montoCtrl = TextEditingController();
  final _referenciaCtrl = TextEditingController();
  List<MetodoPagoModel> _metodos = [];
  int? _idMetodo;
  bool _cargandoMetodos = true;
  bool _enviando = false;

  @override
  void initState() {
    super.initState();
    _cargarMetodos();
  }

  @override
  void dispose() {
    _montoCtrl.dispose();
    _referenciaCtrl.dispose();
    super.dispose();
  }

  void _mensaje(String texto) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(texto)));
  }

  Future<void> _cargarMetodos() async {
    try {
      final data = await _api.get('/api/ventas/metodos-pago');
      final todos = data is List
          ? data.map((j) => MetodoPagoModel.fromJson(j as Map<String, dynamic>)).toList()
          : <MetodoPagoModel>[];
      _metodos = todos
          .where((m) => _metodosPermitidos.contains(m.nombre.trim().toUpperCase()))
          .toList();
      _idMetodo = _metodos.isEmpty ? null : _metodos.first.idMetodoPago;
    } on ApiException catch (e) {
      _mensaje(e.message);
    } finally {
      if (mounted) setState(() => _cargandoMetodos = false);
    }
  }

  double? _monto() => double.tryParse(_montoCtrl.text.trim().replaceAll(',', '.'));

  Future<void> _pagar() async {
    if (!_formKey.currentState!.validate() || _idMetodo == null) return;
    setState(() => _enviando = true);
    try {
      final data = await _api.post('/api/creditos/me/${widget.credit.idCxc}/pagar', {
        'id_metodo_pago': _idMetodo,
        'monto_pagado': _monto(),
        'referencia': _referenciaCtrl.text.trim().isEmpty ? null : _referenciaCtrl.text.trim(),
      });
      final pago = ReportedPayment.fromJson(data as Map<String, dynamic>);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.check_circle, color: AppPalette.success, size: 48),
          title: const Text('Pago informado'),
          content: Text(
            'Informaste un pago de ${formatoBs(pago.monto)}. '
            'Tu saldo se actualizará cuando la empresa confirme el pago.',
            textAlign: TextAlign.center,
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Aceptar')),
          ],
        ),
      );
      if (mounted) Navigator.of(context).pop(pago);
    } on ApiException catch (e) {
      _mensaje(e.message);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final credit = widget.credit;
    return Scaffold(
      appBar: AppBar(title: Text('Pagar crédito #${credit.idCxc}')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Saldo pendiente', style: TextStyle(color: AppPalette.textSoft)),
                      const SizedBox(height: 4),
                      Text(formatoBs(credit.saldoPendiente),
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
                      if (credit.montoInformadoPendiente > 0) ...[
                        const SizedBox(height: 8),
                        Text(
                          'Ya informaste ${formatoBs(credit.montoInformadoPendiente)} pendiente de confirmar. '
                          'Puedes informar hasta ${formatoBs(credit.saldoPorInformar)}.',
                          style: const TextStyle(color: AppPalette.warning, fontSize: 13),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _montoCtrl,
                enabled: !_enviando,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
                decoration: const InputDecoration(labelText: 'Monto a pagar (Bs)', prefixText: 'Bs '),
                validator: (_) {
                  final monto = _monto();
                  if (monto == null || monto <= 0) return 'Ingresa un monto mayor a cero.';
                  return null;
                },
              ),
              const SizedBox(height: 12),
              if (_cargandoMetodos)
                const LinearProgressIndicator()
              else
                DropdownButtonFormField<int>(
                  initialValue: _idMetodo,
                  decoration: const InputDecoration(labelText: 'Método de pago'),
                  items: [
                    for (final m in _metodos)
                      DropdownMenuItem(value: m.idMetodoPago, child: Text(m.nombre)),
                  ],
                  onChanged: _enviando ? null : (v) => setState(() => _idMetodo = v),
                  validator: (v) => v == null ? 'Selecciona el método de pago.' : null,
                ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _referenciaCtrl,
                enabled: !_enviando,
                maxLength: 100,
                decoration: const InputDecoration(
                  labelText: 'Referencia (opcional)',
                  hintText: 'N° de transacción o comprobante',
                ),
              ),
              const SizedBox(height: 8),
              ElevatedButton(
                onPressed: _enviando || _cargandoMetodos ? null : _pagar,
                child: _enviando
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                      )
                    : const Text('Pagar'),
              ),
              const SizedBox(height: 8),
              const Text(
                'El pago queda pendiente hasta que la empresa lo confirme. El efectivo se paga en caja.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppPalette.textSoft, fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
