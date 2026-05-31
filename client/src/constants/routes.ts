// Centralized client-side route paths. Every <Route>, <Link to>, and navigate()
// call should reference these so paths stay consistent and lowercase. These are
// frontend router paths only — backend API endpoints live with their api/ modules.
export const ROUTES = {
  home: "/",
  login: "/login",
  appointments: "/appointments",
  appointmentsSearch: "/appointments/search",
  clients: "/clients",
  employees: "/employees",
  services: "/services",
  settings: "/settings",
  admin: "/admin",
  adminNew: "/admin/new",
  // Router pattern only; build a concrete path with adminOrganizationPath().
  adminOrganization: "/admin/:organizationId",
} as const;

// Concrete path for a single organization's admin detail page.
export const adminOrganizationPath = (organizationId: string | number) =>
  `${ROUTES.admin}/${organizationId}`;

// "All appointments for this phone number" — the search page reads the `pn` query param.
export const appointmentsSearchPath = (phoneNumber: string | undefined) => {
  const params = new URLSearchParams({ pn: phoneNumber ?? "" });
  return `${ROUTES.appointmentsSearch}?${params.toString()}`;
};
