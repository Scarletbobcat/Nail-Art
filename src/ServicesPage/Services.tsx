import { useQuery } from "@tanstack/react-query";
import { getAllServices } from "../api/services";
import { IconButton, InputAdornment, Stack, TextField } from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import CircularLoading from "../components/CircularLoading";
import { useMemo, useState } from "react";
import EditButton from "./components/EditButton";
import { Service } from "../types";
import DeleteButton from "./components/DeleteButton";
import EditServiceModal from "./components/EditModal";
import { Typography, Box, Paper } from "@mui/material";
import DeleteServiceModal from "./components/DeleteModal";
import { useTheme } from "@mui/material/styles";
import SearchIcon from "@mui/icons-material/Search";
import CreateButton from "./components/CreateButton";
import CreateServiceModal from "./components/CreateModal";

export default function Services() {
  const theme = useTheme();
  const [name, setName] = useState("");
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedService, setSelectedService] = useState<Service>({
    id: "",
    name: "",
  });

  const {
    data: services,
    isLoading: servicesLoading,
    error: servicesError,
    refetch: refreshServices,
  } = useQuery({
    queryKey: ["services"],
    queryFn: () => getAllServices(name),
  });

  const columns = [
    { field: "id", headerName: "ID", flex: 1 },
    { field: "name", headerName: "Name", flex: 1 },
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
            <EditButton
              service={params.row.actions}
              setSelectedService={setSelectedService}
              openEdit={() => setIsEditOpen(true)}
            />
            <DeleteButton
              service={params.row.actions}
              setSelectedService={setSelectedService}
              openDelete={() => setIsDeleteOpen(true)}
            />
          </Stack>
        </Box>
      ),
    },
  ];

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      refreshServices();
    }
  };

  const data = useMemo(() => {
    if (!services) return [];
    return services.sort().map((row: Service) => {
      return {
        id: row.id,
        name: row.name,
        actions: row,
      };
    });
  }, [services]);

  if (servicesLoading) return <CircularLoading />;
  if (servicesError) return <div>Error: {servicesError.message}</div>;
  return (
    <Box
      sx={{
        padding: 4,
      }}
    >
      <Paper
        variant="outlined"
        sx={{
          padding: 3,
        }}
      >
        <Stack spacing={2}>
          <Stack
            direction="row"
            sx={{
              backgroundColor: theme.palette.primary.main,
              padding: 2,
              borderRadius: 2,
            }}
          >
            <Typography variant="h4" sx={{ color: "white" }}>
              Services
            </Typography>
          </Stack>
          <Stack direction="row" spacing={2} justifyContent="space-between">
            <Stack direction="row" spacing={2}>
              <TextField
                label="Name"
                onChange={(e) => setName(e.target.value)}
                onKeyDown={handleKeyDown}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={async () => {
                            refreshServices();
                          }}
                        >
                          <SearchIcon />
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                }}
              />
            </Stack>
            <CreateButton openCreate={() => setIsCreateOpen(true)} />
          </Stack>
          <DataGrid
            rows={data}
            columns={columns}
            disableRowSelectionOnClick
            initialState={{
              pagination: {
                paginationModel: {
                  pageSize: 10,
                },
              },
            }}
            pageSizeOptions={[10]}
          />
        </Stack>
        {isEditOpen && (
          <EditServiceModal
            service={selectedService}
            isOpen={isEditOpen}
            onClose={() => setIsEditOpen(false)}
            renderServices={refreshServices}
          />
        )}
        {isDeleteOpen && (
          <DeleteServiceModal
            service={selectedService}
            isOpen={isDeleteOpen}
            onClose={() => setIsDeleteOpen(false)}
            renderServices={refreshServices}
          />
        )}
        {isCreateOpen && (
          <CreateServiceModal
            renderServices={refreshServices}
            isOpen={isCreateOpen}
            onClose={() => setIsCreateOpen(false)}
          />
        )}
      </Paper>
    </Box>
  );
}
