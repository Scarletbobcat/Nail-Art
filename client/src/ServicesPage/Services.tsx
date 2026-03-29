import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createService,
  deleteService,
  editService,
  getServicesPaginated,
} from "../api/services";
import { Stack, TextField, Button, TablePagination } from "@mui/material";
import PageSkeleton from "../components/PageSkeleton";
import AnimatedPage from "../components/AnimatedPage";
import { useMemo, useState } from "react";
import { Service } from "../types";
import { Box, Paper } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ServiceModal from "./components/ServiceModal";
import PlusIcon from "@mui/icons-material/Add";
import PageHeader from "../components/PageHeader";
import CardList from "../components/CardList";
import { SPACING, MAX_CONTENT_WIDTH } from "../constants/design";

export default function Services() {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [isLoading, setIsLoading] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedService, setSelectedService] = useState<Service>({
    id: undefined,
    name: "",
  });

  const {
    data: servicesData,
    isLoading: servicesLoading,
    error: servicesError,
    refetch,
  } = useQuery({
    queryKey: ["services", page, rowsPerPage],
    queryFn: () => getServicesPaginated(name, page, rowsPerPage),
  });

  const refreshServices = async () => {
    setIsLoading(true);
    setPage(0);
    await refetch();
    queryClient.invalidateQueries({ queryKey: ["services"] });
    setIsLoading(false);
  };

  const handleKeyDown = async (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter") {
      refreshServices();
    }
  };

  const totalElements = servicesData?.totalElements ?? 0;

  const data = useMemo(() => {
    if (!servicesData?.content) return [];
    return servicesData.content.map((row: Service) => ({
      id: row.id ?? 0,
      name: row.name,
      _raw: row,
    }));
  }, [servicesData]);

  if (servicesLoading || isLoading) return <PageSkeleton />;
  if (servicesError) return <div>Error: {servicesError.message}</div>;

  return (
    <AnimatedPage>
    <Box sx={{ p: SPACING.page, maxWidth: MAX_CONTENT_WIDTH, mx: "auto" }}>
      <Paper variant="outlined" sx={{ p: SPACING.section }}>
        <PageHeader
          title="Services"
          subtitle="Manage your service offerings"
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
            name="service"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <Button
            variant="contained"
            startIcon={<SearchIcon />}
            onClick={refreshServices}
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
              onClick={refreshServices}
              sx={{ flex: 1, height: 40 }}
            >
              Search
            </Button>
          </Stack>
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
    </AnimatedPage>
  );
}
