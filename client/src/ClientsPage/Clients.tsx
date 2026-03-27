import {
  createClient,
  deleteClient,
  editClient,
  getClients,
} from "../api/clients";
import { Box, Paper, Stack, TextField, Button } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import SearchIcon from "@mui/icons-material/Search";
import PlusIcon from "@mui/icons-material/Add";
import { Client } from "../types/Client";
import CircularLoading from "../components/CircularLoading";
import ClientModal from "./components/ClientModal";
import PageHeader from "../components/PageHeader";
import CardList from "../components/CardList";
import CustomButton from "../components/Button";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";

export default function Clients() {
  const [isLoading, setIsLoading] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [form, setForm] = useState<{ name?: string; phoneNumber?: string }>({});
  const {
    data: clients,
    isLoading: clientsLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ["clients"],
    queryFn: () => getClients(form),
  });
  const [selectedClient, setSelectedClient] = useState<Client>({
    id: "",
    name: "",
    phoneNumber: "",
    appointmentIds: [],
  });

  const refreshClients = async () => {
    setIsLoading(true);
    await refetch();
    setIsLoading(false);
  };

  const data = useMemo(() => {
    if (!clients) return [];
    return clients.map((row: Client) => ({
      id: row.id,
      name: row.name,
      phoneNumber: row.phoneNumber,
      _raw: row,
    }));
  }, [clients]);

  if (clientsLoading || isLoading) {
    return <CircularLoading />;
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
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Clients"
          subtitle="Manage your client directory"
          action={
            <Button
              variant="contained"
              startIcon={<PlusIcon />}
              onClick={() => setIsCreateOpen(true)}
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
          <CustomButton
            text="Search"
            color="primary"
            onClick={refreshClients}
            Icon={SearchIcon}
          />
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
  );
}
