class ActiveSubscription {
  final String estado;
  final DateTime? fechaFin;
  final String planNombre;

  const ActiveSubscription({
    required this.estado,
    this.fechaFin,
    required this.planNombre,
  });

  factory ActiveSubscription.fromJson(Map<String, dynamic> json) {
    DateTime? fin;
    if (json['fecha_fin'] != null) {
      fin = DateTime.tryParse(json['fecha_fin'].toString());
    }
    return ActiveSubscription(
      estado: json['estado']?.toString() ?? '',
      fechaFin: fin,
      planNombre: json['plan_nombre']?.toString() ?? '',
    );
  }
}

class Company {
  final int idEmpresa;
  final String nombre;
  final String razonSocial;
  final String nit;
  final String correo;
  final DateTime fechaCreacion;
  final bool activo;
  final ActiveSubscription? suscripcionActiva;

  const Company({
    required this.idEmpresa,
    required this.nombre,
    required this.razonSocial,
    required this.nit,
    required this.correo,
    required this.fechaCreacion,
    required this.activo,
    this.suscripcionActiva,
  });

  factory Company.fromJson(Map<String, dynamic> json) {
    final activeSubJson = json['suscripcion_activa'];
    return Company(
      idEmpresa: int.tryParse(json['id_empresa']?.toString() ?? '') ?? 0,
      nombre: json['nombre']?.toString() ?? '',
      razonSocial: json['razon_social']?.toString() ?? '',
      nit: json['nit']?.toString() ?? '',
      correo: json['correo']?.toString() ?? '',
      fechaCreacion: DateTime.tryParse(json['fecha_creacion']?.toString() ?? '') ?? DateTime.now(),
      activo: json['activo'] == true,
      suscripcionActiva: activeSubJson != null ? ActiveSubscription.fromJson(activeSubJson as Map<String, dynamic>) : null,
    );
  }
}
