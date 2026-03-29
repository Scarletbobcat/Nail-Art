import {
  Button,
  Box,
  TextField,
  Stack,
  MenuItem,
  InputLabel,
  FormControl,
  CircularProgress,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import Select, { SelectChangeEvent } from "@mui/material/Select";
import Chip from "@mui/material/Chip";
import {
  DateTimePicker,
  MobileDatePicker,
  MobileTimePicker,
  LocalizationProvider,
} from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs from "dayjs";
import { useState, FormEvent, useCallback } from "react";
import { Appointment, Employee, Service, Alert, Client } from "../../types";
import CustomAlert from "../../components/Alert";
import ClientSelect from "./ClientSelect";
import { DateTimeValidationError } from "@mui/x-date-pickers/models";
import { TimeValidationError } from "@mui/x-date-pickers/models";
import ResponsiveModal from "../../components/ResponsiveModal";

const nineAM = dayjs().hour(9).minute(0).second(0);
const ninePM = dayjs().hour(21).minute(0).second(0);

export default function AppointmentModal({
  appointment,
  onClose,
  isOpen,
  renderEvents,
  allServices,
  allEmployees,
  onSubmit,
  type,
  clients,
}: {
  appointment: Appointment;
  onClose: () => void;
  isOpen: boolean;
  renderEvents: (form?: Appointment) => void;
  allServices: Service[];
  allEmployees: Employee[];
  onSubmit: (form: Appointment) => void;
  type: "delete" | "edit" | "create";
  clients?: Client[];
}) {
  const [form, setForm] = useState<Appointment>({
    ...appointment,
  });
  const [isLoading, setIsLoading] = useState(false);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [alert, setAlert] = useState<Alert>({
    message: "",
    severity: "error",
  });
  const [startError, setStartError] = useState<DateTimeValidationError | null>(
    null
  );
  const [endError, setEndError] = useState<DateTimeValidationError | null>(
    null
  );
  const [endTimeCustomError, setEndTimeCustomError] = useState<string>("");
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  const errorMessage = useCallback((error: DateTimeValidationError | null) => {
    switch (error) {
      case "maxTime": {
        return "Select a time during work hours";
      }
      case "minTime": {
        return "Select a time during work hours";
      }

      case "invalidDate": {
        return "Your time is not valid";
      }

      default: {
        return "";
      }
    }
  }, []);

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
    }
  }

  const handleSave = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      setIsLoading(true);
      // save the appointment
      await onSubmit(form);
      setIsLoading(false);
      // closing modal and re-rendering events
      onClose();
      renderEvents(form);
    } catch {
      // show alert if failed to create appointment
      setIsAlertOpen(true);
      setAlert({
        message: `Failed to ${type} appointment`,
        severity: "error",
      });
      setIsLoading(false);
    }
  };

  return (
    <div>
      <CustomAlert
        isOpen={isAlertOpen}
        message={alert.message}
        severity={alert.severity}
        onClose={() => setIsAlertOpen(false)}
      />
      <ResponsiveModal
        open={isOpen}
        onClose={onClose}
        title={`${type.charAt(0).toUpperCase() + type.slice(1)} Appointment`}
        maxWidth={545}
      >
        <Box
          component="form"
          onSubmit={(e: React.FormEvent<HTMLFormElement>) => {
            if (!startError && !endError && !endTimeCustomError) {
              handleSave(e);
            } else {
              e.preventDefault();
            }
          }}
        >
          <Stack spacing={2}>
            <Stack>
              <Stack
                sx={{
                  width: "100%",
                }}
              >
                {clients && (
                  <ClientSelect
                    onChange={(client) =>
                      setForm({
                        ...form,
                        name: client.name,
                        phoneNumber: client.phoneNumber,
                        clientId: client.clientId,
                      })
                    }
                    show={type === "create"}
                    clients={clients}
                  />
                )}
              </Stack>
            </Stack>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                fullWidth
                required
                disabled={type === "delete"}
                label="Name"
                name="name"
                value={form.name || ""}
                variant="outlined"
                onChange={(e) => {
                  setForm({ ...form, name: e.target.value });
                }}
              />
              <TextField
                fullWidth
                disabled={type === "delete"}
                label="Phone Number"
                name="phoneNumber"
                value={form.phoneNumber}
                variant="outlined"
                inputProps={{ inputMode: "tel" }}
                onChange={(e) => changePhoneNumber(e.target.value)}
              />
            </Stack>
            <Stack spacing={2}>
              <LocalizationProvider dateAdapter={AdapterDayjs}>
                {isMobile ? (
                  <Stack spacing={2}>
                    <MobileDatePicker
                      label="Date"
                      disabled={type === "delete"}
                      value={form ? dayjs(form.date + form.startTime) : dayjs()}
                      onChange={(date) => {
                        setForm({
                          ...form,
                          date: date ? date.format("YYYY-MM-DD") : form.date,
                        });
                      }}
                    />
                    <Stack direction="row" spacing={2}>
                      <MobileTimePicker
                        label="Start Time"
                        disabled={type === "delete"}
                        minTime={nineAM}
                        maxTime={ninePM}
                        minutesStep={15}
                        onError={(newError: TimeValidationError) =>
                          setStartError(newError)
                        }
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            helperText: errorMessage(startError),
                          },
                        }}
                        value={
                          form ? dayjs(form.date + form.startTime) : dayjs()
                        }
                        onChange={(time) => {
                          setForm({
                            ...form,
                            startTime: time
                              ? "T" + time.format("HH:mm:ss")
                              : form.startTime,
                          });
                        }}
                      />
                      <MobileTimePicker
                        label="End Time"
                        disabled={type === "delete"}
                        minTime={nineAM}
                        maxTime={ninePM}
                        minutesStep={15}
                        onError={(newError: TimeValidationError) =>
                          setEndError(newError)
                        }
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            helperText:
                              endTimeCustomError || errorMessage(endError),
                          },
                        }}
                        value={
                          form ? dayjs(form.date + form.endTime) : dayjs()
                        }
                        onChange={(time) => {
                          if (time && form.startTime) {
                            const startDateTime = dayjs(
                              form.date + form.startTime
                            );
                            const endDateTime = time
                              .year(startDateTime.year())
                              .month(startDateTime.month())
                              .date(startDateTime.date());
                            if (
                              endDateTime.isSame(startDateTime) ||
                              endDateTime.isBefore(startDateTime)
                            ) {
                              setEndTimeCustomError(
                                "End time must be after start time"
                              );
                            } else {
                              setEndTimeCustomError("");
                            }
                          } else {
                            setEndTimeCustomError("");
                          }
                          setForm({
                            ...form,
                            endTime: time
                              ? "T" + time.format("HH:mm:ss")
                              : form.endTime,
                          });
                        }}
                      />
                    </Stack>
                  </Stack>
                ) : (
                  <Stack direction="row" spacing={2}>
                    <DateTimePicker
                      label="Start"
                      disabled={type === "delete"}
                      minTime={nineAM}
                      maxTime={ninePM}
                      minutesStep={15}
                      onError={(newError) => setStartError(newError)}
                      slotProps={{
                        textField: {
                          helperText: errorMessage(startError),
                        },
                      }}
                      value={
                        form ? dayjs(form.date + form.startTime) : dayjs()
                      }
                      onChange={(date) => {
                        setForm({
                          ...form,
                          date: date ? date.format("YYYY-MM-DD") : form.date,
                          startTime: date
                            ? "T" + date.format("HH:mm:ss")
                            : form.startTime,
                        });
                      }}
                    />
                    <DateTimePicker
                      value={form ? dayjs(form.date + form.endTime) : dayjs()}
                      label="End"
                      minTime={nineAM}
                      maxTime={ninePM}
                      minutesStep={15}
                      shouldDisableTime={(time, view) => {
                        if (!form.startTime) return true;
                        const startDateTime = dayjs(
                          form.date + form.startTime
                        );
                        if (view === "minutes" || view === "hours") {
                          return (
                            time.isSame(startDateTime) ||
                            time.isBefore(startDateTime)
                          );
                        }
                        return false;
                      }}
                      disabled={type === "delete"}
                      onError={(newError) => setEndError(newError)}
                      slotProps={{
                        textField: {
                          helperText:
                            endTimeCustomError || errorMessage(endError),
                        },
                      }}
                      onChange={(date) => {
                        if (date && form.startTime) {
                          const startDateTime = dayjs(
                            form.date + form.startTime
                          );
                          if (
                            date.isSame(startDateTime) ||
                            date.isBefore(startDateTime)
                          ) {
                            setEndTimeCustomError(
                              "End time must be after start time"
                            );
                          } else {
                            setEndTimeCustomError("");
                          }
                        } else {
                          setEndTimeCustomError("");
                        }
                        setForm({
                          ...form,
                          date: date ? date.format("YYYY-MM-DD") : form.date,
                          endTime: date
                            ? "T" + date.format("HH:mm:ss")
                            : form.endTime,
                        });
                      }}
                    />
                  </Stack>
                )}
              </LocalizationProvider>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                <FormControl fullWidth>
                  <InputLabel id="employee-label">Employee</InputLabel>
                  <Select
                    labelId="employee-label"
                    disabled={type === "delete"}
                    fullWidth
                    required
                    label="Employee"
                    value={form.employeeId}
                    onChange={(e: SelectChangeEvent) => {
                      setForm({ ...form, employeeId: e.target.value });
                    }}
                    variant="outlined"
                  >
                    {allEmployees.map((employee) => (
                      <MenuItem key={employee.id} value={employee.id}>
                        {employee.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="service-label">Services</InputLabel>
                  <Select<number[]>
                    labelId="service-label"
                    disabled={type === "delete"}
                    multiple
                    required
                    fullWidth
                    label="Service"
                    value={form.services}
                    onChange={(e) => {
                      const value = e.target.value;
                      setForm({
                        ...form,
                        services: (typeof value === "string" ? value.split(",").map(Number) : value),
                      });
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                        {selected.map((value) => {
                          const serviceName = allServices.find(
                            (s) => s.id === value
                          )?.name;
                          return <Chip key={value} label={serviceName} />;
                        })}
                      </Box>
                    )}
                    variant="outlined"
                  >
                    {allServices.map((service) => (
                      <MenuItem key={service.id} value={service.id}>
                        {service.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Stack>
            </Stack>
          </Stack>
          <Box sx={{ mt: 3, display: "flex", justifyContent: "right" }}>
            <Stack direction="row" spacing={1}>
              <Button onClick={onClose} color="info">
                Cancel
              </Button>
              <Button
                type="submit"
                color={type === "delete" ? "error" : "primary"}
                variant="contained"
                disabled={
                  isLoading ||
                  Boolean(startError) ||
                  Boolean(endError) ||
                  Boolean(endTimeCustomError)
                }
                endIcon={isLoading ? <CircularProgress size={20} /> : null}
              >
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </Button>
            </Stack>
          </Box>
        </Box>
      </ResponsiveModal>
    </div>
  );
}
