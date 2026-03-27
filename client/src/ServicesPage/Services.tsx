import { useQuery } from "@tanstack/react-query";
import {
  createService,
  deleteService,
  editService,
  getAllServices,
} from "../api/services";
import { Stack, TextField, Button } from "@mui/material";
import CircularLoading from "../components/CircularLoading";
import { useMemo, useState } from "react";
import { Service } from "../types";
import { Box, Paper } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ServiceModal from "./components/ServiceModal";
import CustomButton from "../components/Button";
import PlusIcon from "@mui/icons-material/Add";
import PageHeader from "../components/PageHeader";
import CardList from "../components/CardList";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";

export default function Services() {
  const [name, setName] = useState("");
  const [isLoading, setIsLoading] = useState(false);
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
    refetch,
  } = useQuery({
    queryKey: ["services"],
    queryFn: () => getAllServices(name),
  });

  const refreshServices = async () => {
    setIsLoading(true);
    await refetch();
    setIsLoading(false);
  };

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      refreshServices();
    }
  };

  const data = useMemo(() => {
    if (!services) return [];
    return services.sort().map((row: Service) => ({
      id: row.id ?? 0,
      name: row.name,
      _raw: row,
    }));
  }, [services]);

  if (servicesLoading || isLoading) return <CircularLoading />;
  if (servicesError) return <div>Error: {servicesError.message}</div>;

  return (
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Services"
          subtitle="Manage your service offerings"
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
            name="service"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <CustomButton
            text="Search"
            onClick={refreshServices}
            Icon={SearchIcon}
            color="primary"
          />
        </Stack>

        <CardList
          data={data}
          emptyMessage="No services found"
          renderPrimary={(item) => item.name}
          onEdit={(item) => {
            setSelectedService(item._raw);
            setIsEditOpen(true);
          }}
          onDelete={(item) => {
            setSelectedService(item._raw);
            setIsDeleteOpen(true);
          }}
        />
      </Paper>
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
    </Box>
  );
}
