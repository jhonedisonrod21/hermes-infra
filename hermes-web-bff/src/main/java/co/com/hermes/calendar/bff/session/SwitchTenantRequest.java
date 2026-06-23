package co.com.hermes.calendar.bff.session;

/** Organización a la que cambiar el token activo de la sesión (validado en el controlador). */
public record SwitchTenantRequest(String tenantId) {
}
