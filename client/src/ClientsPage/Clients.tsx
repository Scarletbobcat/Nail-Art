import {
  createClient,
  deleteClient,
  editClient,
  getClients,
} from "../api/clients";
import { Box, Paper, Stack, Typography, TextField } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import { useTheme } from "@mui/material/styles";
import { useMemo, useState } from "react";
import SearchIcon from "@mui/icons-material/Search";
import PlusIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import CustomButton from "../components/Button";
import { Client } from "../types/Client";
import CircularLoading from "../components/CircularLoading";
import ClientModal from "./components/ClientModal";

export default function Clients() {
  const theme = useTheme();
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
    return clients.map((row: Client) => {
      return {
        id: row.id,
        name: row.name,
        phoneNumber: row.phoneNumber,
        actions: row,
      };
    });
  }, [clients]);

  if (clientsLoading || isLoading) {
    return <CircularLoading />;
  }

  const columns = [
    { field: "id", headerName: "ID", flex: 1 },
    { field: "name", headerName: "Name", flex: 1 },
    { field: "phoneNumber", headerName: "Phone Number", flex: 1 },
    {
      field: "Actions",
      headerName: "Actions",
      flex: 1,
      renderCell: (params: GridRenderCellParams) => (
        <Box
          sx={{
            display: "flex",
            justifyContent: "left",
            alignItems: "center",
            width: "100%",
            height: "100%",
          }}
        >
          <Stack direction="row" spacing={2}>
            <CustomButton
              Icon={EditIcon}
              color="primary"
              onClick={() => {
                setSelectedClient(params.row.actions);
                setIsEditOpen(true);
              }}
            />
            <CustomButton
              color="error"
              Icon={DeleteIcon}
              onClick={() => {
                setSelectedClient(params.row.actions);
                setIsDeleteOpen(true);
              }}
            />
          </Stack>
        </Box>
      ),
    },
  ];

  if (isLoading) {
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
      // conditionally adds hyphen only when adding to phone number, not deleting
      if (
        (newPN.length === 3 && form.phoneNumber?.length === 2) ||
        (newPN.length === 7 && form.phoneNumber?.length === 6)
      ) {
        newPN += "-";
      }
      setForm({ ...form, phoneNumber: newPN });
    } else {
      // console.error("Phone number does not match regex");
    }
  }

  return (
    <Box
      sx={{
        padding: 4,
        height: "calc(100vh)",
      }}
    >
      <Paper
        variant="outlined"
        sx={{
          padding: 3,
          height: "100%",
        }}
      >
        <Stack
          spacing={2}
          sx={{ height: "100%" }}
          justifyContent={"space-between"}
        >
          <Stack
            direction="row"
            sx={{
              backgroundColor: theme.palette.primary.main,
              padding: 2,
              borderRadius: 2,
            }}
            justifyContent="space-between"
          >
            <Typography variant="h4" sx={{ color: "white" }}>
              Clients
            </Typography>
            <CustomButton
              text="Create"
              color="secondary"
              sx={{
                color: "white",
              }}
              onClick={() => setIsCreateOpen(true)}
              Icon={PlusIcon}
            />
          </Stack>
          <Stack direction="row" spacing={2} justifyContent="space-between">
            <Stack direction="row" spacing={2}>
              <TextField
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
            </Stack>
            <CustomButton
              text="Search"
              color="primary"
              onClick={refreshClients}
              Icon={SearchIcon}
            />
          </Stack>
          <DataGrid
            rows={data}
            columns={columns}
            initialState={{
              pagination: {
                pageSize: 10,
              },
            }}
            rowsPerPageOptions={[10]}
            disableSelectionOnClick
          />
        </Stack>
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
