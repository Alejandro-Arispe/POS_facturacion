class NotificationModel {
  final int id;
  final int? idEmpresa;
  final int prioridad;
  final String tipo;
  final String titulo;
  final String mensaje;
  final Map<String, dynamic>? payload;
  final bool leido;
  final DateTime fecha;

  const NotificationModel({
    required this.id,
    this.idEmpresa,
    required this.prioridad,
    required this.tipo,
    required this.titulo,
    required this.mensaje,
    this.payload,
    required this.leido,
    required this.fecha,
  });

  factory NotificationModel.fromJson(Map<String, dynamic> json) {
    String fechaStr = json['fecha']?.toString() ?? '';
    DateTime parsedDate;
    if (fechaStr.isNotEmpty) {
      if (!fechaStr.endsWith('Z') && !fechaStr.contains('+') && !RegExp(r'-\d{2}:\d{2}$').hasMatch(fechaStr)) {
        fechaStr = '${fechaStr}Z';
      }
      parsedDate = DateTime.tryParse(fechaStr)?.toLocal() ?? DateTime.now();
    } else {
      parsedDate = DateTime.now();
    }

    return NotificationModel(
      id: int.tryParse(json['id']?.toString() ?? '') ?? 0,
      idEmpresa: json['id_empresa'] != null ? int.tryParse(json['id_empresa'].toString()) : null,
      prioridad: int.tryParse(json['prioridad']?.toString() ?? '') ?? 0,
      tipo: json['tipo']?.toString() ?? 'GENERAL',
      titulo: json['titulo']?.toString() ?? '',
      mensaje: json['mensaje']?.toString() ?? '',
      payload: json['payload'] is Map<String, dynamic> ? json['payload'] as Map<String, dynamic> : null,
      leido: json['leido'] == true,
      fecha: parsedDate,
    );
  }
}
