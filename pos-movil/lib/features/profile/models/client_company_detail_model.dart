import 'client_profile_model.dart';

class PersonaEmployee {
  final int idPersona;
  final String nombreCompleto;
  final DateTime fechaNacimiento;
  final String genero;
  final String telefono;
  final String documento;

  const PersonaEmployee({
    required this.idPersona,
    required this.nombreCompleto,
    required this.fechaNacimiento,
    required this.genero,
    required this.telefono,
    required this.documento,
  });

  factory PersonaEmployee.fromJson(Map<String, dynamic> json) {
    return PersonaEmployee(
      idPersona: int.tryParse(json['id_persona']?.toString() ?? '') ?? 0,
      nombreCompleto: json['nombre_completo']?.toString() ?? '',
      fechaNacimiento: DateTime.tryParse(json['fecha_nacimiento']?.toString() ?? '') ?? DateTime.now(),
      genero: json['genero']?.toString() ?? '',
      telefono: json['telefono']?.toString() ?? '',
      documento: json['documento']?.toString() ?? '',
    );
  }
}

class UserEmployee {
  final int idUsuario;
  final String email;
  final bool activo;
  final PersonaEmployee persona;

  const UserEmployee({
    required this.idUsuario,
    required this.email,
    required this.activo,
    required this.persona,
  });

  factory UserEmployee.fromJson(Map<String, dynamic> json) {
    return UserEmployee(
      idUsuario: int.tryParse(json['id_usuario']?.toString() ?? '') ?? 0,
      email: json['email']?.toString() ?? '',
      activo: json['activo'] == true,
      persona: PersonaEmployee.fromJson(json['persona'] as Map<String, dynamic>),
    );
  }
}

class ClientCompanyDetail {
  final int idUsuarioRol;
  final int idUsuario;
  final int idRol;
  final int idEmpresa;
  final int? idSucursal;
  final bool activo;
  final UserEmployee usuario;
  final ClientProfile? cliente;

  const ClientCompanyDetail({
    required this.idUsuarioRol,
    required this.idUsuario,
    required this.idRol,
    required this.idEmpresa,
    this.idSucursal,
    required this.activo,
    required this.usuario,
    this.cliente,
  });

  factory ClientCompanyDetail.fromJson(Map<String, dynamic> json) {
    final clientJson = json['cliente'];
    return ClientCompanyDetail(
      idUsuarioRol: int.tryParse(json['id_usuario_rol']?.toString() ?? '') ?? 0,
      idUsuario: int.tryParse(json['id_usuario']?.toString() ?? '') ?? 0,
      idRol: int.tryParse(json['id_rol']?.toString() ?? '') ?? 0,
      idEmpresa: int.tryParse(json['id_empresa']?.toString() ?? '') ?? 0,
      idSucursal: json['id_sucursal'] != null ? int.tryParse(json['id_sucursal'].toString()) : null,
      activo: json['activo'] == true,
      usuario: UserEmployee.fromJson(json['usuario'] as Map<String, dynamic>),
      cliente: clientJson != null ? ClientProfile.fromJson(clientJson as Map<String, dynamic>) : null,
    );
  }
}
