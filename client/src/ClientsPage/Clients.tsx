import {
  createClient,
  deleteClient,
  editClient,
  getClientsPaginated,
} from "../api/clients";
import { Box, Paper, Stack, TextField, Button, TablePagination } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import SearchIcon from "@mui/icons-material/Search";
import PlusIcon from "@mui/icons-material/Add";
import { Client } from "../types/Client";
import PageSkeleton from "../components/PageSkeleton";
import AnimatedPage from "../components/AnimatedPage";
import ClientModal from "./components/ClientModal";
import PageHeader from "../components/PageHeader";
import CardList from "../components/CardList";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";

export default function Clients() {
  const [isLoading, setIsLoading] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [form, setForm] = useState<{ name?: string; phoneNumber?: string }>({});
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const {
    data: clientsData,
    isLoading: clientsLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ["clients", page, rowsPerPage],
    queryFn: () => getClientsPaginated({ ...form, page, size: rowsPerPage }),
  });
  const [selectedClient, setSelectedClient] = useState<Client>({
    id: "",
    name: "",
    phoneNumber: "",
    appointmentIds: [],
  });

  const refreshClients = async () => {
    setIsLoading(true);
    setPage(0);
    await refetch();
    setIsLoading(false);
  };

  const totalElements = clientsData?.totalElements ?? 0;

  const data = useMemo(() => {
    if (!clientsData?.content) return [];
    return clientsData.content.map((row: Client) => ({
      id: row.id,
      name: row.name,
      phoneNumber: row.phoneNumber,
      _raw: row,
    }));
  }, [clientsData]);

  if (clientsLoading || isLoading) {
    return <PageSkeleton />;
  }

  if (error) {
    return <div>Error: {error.message}</div>;
  }

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      refreshClients();
    }
  };

  function changePhoneNumber(inputPhoneNumber: string) {
    const regex = /^\d{0,3}[\s-]?\d{0,3}[\s-]?\d{0,4}$/;
    if (regex.test(inputPhoneNumber)) {
      let newPN = inputPhoneNumber;
      if (
        (newPN.length === 3 && form.phoneNumber?.length === 2) ||
        (newPN.length === 7 && form.phoneNumber?.length === 6)
      ) {
        newPN += "-";
      }
      setForm({ ...form, phoneNumber: newPN });
    }
  }

  return (
    <AnimatedPage>
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Clients"
          subtitle="Manage your client directory"
          action={
            <Button
              variant="contained"
              size="small"
              startIcon={<PlusIcon />}
              onClick={() => setIsCreateOpen(true)}
              sx={{ display: { xs: "none", sm: "inline-flex" }, height: 40, minWidth: 120 }}
            >
              Create
            </Button>
          }
        />
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={2}
          sx={{ mb: 2 }}
          alignItems={{ sm: "center" }}
        >
          <TextField
            fullWidth
            size="small"
            label="Name"
            name="name"
            value={form.name || ""}
            onChange={(e) => {
              if (e.target.value === "") {
                const { name: _removedName, ...rest } = form; // eslint-disable-line @typescript-eslint/no-unused-vars
                setForm(rest);
              } else {
                setForm({ ...form, name: e.target.value });
              }
            }}
            onKeyDown={handleKeyDown}
          />
          <TextField
            fullWidth
            size="small"
            label="Phone Number"
            name="phoneNumber"
            value={form.phoneNumber || ""}
            onChange={(e) => {
              if (e.target.value === "") {
                const { phoneNumber: _unusedPhoneNumber, ...rest } = form; // eslint-disable-line @typescript-eslint/no-unused-vars
                setForm(rest);
              } else {
                changePhoneNumber(e.target.value);
              }
            }}
            onKeyDown={handleKeyDown}
          />
          <Button
            variant="contained"
            size="small"
            startIcon={<SearchIcon />}
            onClick={refreshClients}
            sx={{ height: 40, display: { xs: "none", sm: "inline-flex" }, flexShrink: 0, minWidth: 120 }}
          >
            Search
          </Button>
          <Stack direction="row" spacing={1.5} sx={{ display: { xs: "flex", sm: "none" }, width: "100%" }}>
            <Button
              variant="outlined"
              startIcon={<PlusIcon />}
              onClick={() => setIsCreateOpen(true)}
              sx={{ flex: 1, height: 40 }}
            >
              Create
            </Button>
            <Button
              variant="contained"
              startIcon={<SearchIcon />}
              onClick={refreshClients}
              sx={{ flex: 1, height: 40 }}
            >
              Search
            </Button>
          </Stack>
        </Stack>

        <CardList
          data={data}
          emptyMessage="No clients found"
          renderPrimary={(item) => item.name}
          renderSecondary={(item) => item.phoneNumber}
          onEdit={(item) => {
            setSelectedClient(item._raw);
            setIsEditOpen(true);
          }}
          onDelete={(item) => {
            setSelectedClient(item._raw);
            setIsDeleteOpen(true);
          }}
        />
        {totalElements > 0 && (
          <TablePagination
            component="div"
            count={totalElements}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(parseInt(e.target.value, 10));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50]}
            sx={{ borderTop: "1px solid", borderColor: "divider", mt: 2 }}
          />
        )}
      </Paper>
      {isCreateOpen && (
        <ClientModal
          type="create"
          onSubmit={createClient}
          client={{ id: "", name: "", phoneNumber: "", appointmentIds: [] }}
          renderEntities={refreshClients}
          isOpen={isCreateOpen}
          onClose={() => setIsCreateOpen(false)}
        />
      )}
      {isEditOpen && (
        <ClientModal
          type="edit"
          client={selectedClient}
          onSubmit={editClient}
          renderEntities={refreshClients}
          isOpen={isEditOpen}
          onClose={() => setIsEditOpen(false)}
        />
      )}
      {isDeleteOpen && (
        <ClientModal
          type="delete"
          client={selectedClient}
          onSubmit={deleteClient}
          renderEntities={refreshClients}
          isOpen={isDeleteOpen}
          onClose={() => setIsDeleteOpen(false)}
        />
      )}
    </Box>
    </AnimatedPage>
  );
}
