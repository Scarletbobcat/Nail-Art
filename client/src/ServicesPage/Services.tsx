import { useQuery } from "@tanstack/react-query";
import {
  createService,
  deleteService,
  editService,
  getAllServices,
} from "../api/services";
import { IconButton, InputAdornment, Stack, TextField } from "@mui/material";
import { DataGrid, GridRenderCellParams } from "@mui/x-data-grid";
import CircularLoading from "../components/CircularLoading";
import { useMemo, useState } from "react";
import { Service } from "../types";
import { Typography, Box, Paper } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import SearchIcon from "@mui/icons-material/Search";
import ServiceModal from "./components/ServiceModal";
import CustomButton from "../components/Button";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import PlusIcon from "@mui/icons-material/Add";

export default function Services() {
  const theme = useTheme();
  const [name, setName] = useState("");
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedService, setSelectedService] = useState<Service>({
    id: undefined,
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
            <CustomButton
              Icon={EditIcon}
              color="primary"
              onClick={() => {
                setSelectedService(params.row.actions);
                setIsEditOpen(true);
              }}
            />
            <CustomButton
              Icon={DeleteIcon}
              color="error"
              onClick={() => {
                setSelectedService(params.row.actions);
                setIsDeleteOpen(true);
              }}
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
        height: "100vh",
      }}
    >
      <Paper
        variant="outlined"
        sx={{
          padding: 3,
          height: "100%",
        }}
      >
        <Stack spacing={2} sx={{ height: "100%" }}>
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
                InputProps={{
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
                }}
              />
            </Stack>
            <CustomButton
              color="primary"
              text={"Create"}
              Icon={PlusIcon}
              onClick={() => setIsCreateOpen(true)}
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
            disableSelectionOnClick
          />
        </Stack>
        {isEditOpen && (
          <ServiceModal
            type={"edit"}
            onSubmit={editService}
            service={selectedService}
            isOpen={isEditOpen}
            onClose={() => setIsEditOpen(false)}
            renderServices={refreshServices}
          />
        )}
        {isDeleteOpen && (
          <ServiceModal
            type={"delete"}
            onSubmit={deleteService}
            service={selectedService}
            isOpen={isDeleteOpen}
            onClose={() => setIsDeleteOpen(false)}
            renderServices={refreshServices}
          />
        )}
        {isCreateOpen && (
          <ServiceModal
            service={selectedService}
            type={"create"}
            onSubmit={createService}
            renderServices={refreshServices}
            isOpen={isCreateOpen}
            onClose={() => setIsCreateOpen(false)}
          />
        )}
      </Paper>
    </Box>
  );
}
