import { Autocomplete, TextField, Typography, Stack } from "@mui/material";
import { useState } from "react";
import { Client } from "../../types/Client";

export default function ClientSelect({
  onChange,
  show,
  clients,
}: {
  onChange: (client: {
    name: string;
    phoneNumber: string;
    clientId?: number | undefined;
  }) => void;
  show?: boolean;
  clients: Client[];
}) {
  const [options] = useState<Client[]>([...clients]);
  const [selectedClient, setSelectedClient] = useState<Client | null>({
    id: "-1",
    name: "New Client",
  });

  const filteredOptions = (options: Client[], inputValue: string) => {
    const filtered = options.filter((option) => {
      return (
        option.name.toLowerCase().includes(inputValue.toLowerCase()) ||
        option.phoneNumber?.includes(inputValue)
      );
    });

    return filtered;
  };

  return (
    show && (
      <Autocomplete
        id="client-select"
        value={selectedClient}
        options={[{ id: "-1", name: "New Client" }, ...options]}
        getOptionLabel={(option) => {
          return option.name;
        }}
        getOptionKey={(option: Client) => option.phoneNumber || ""}
        renderOption={(props, option: Client) => {
          return (
            <li {...props} key={`${option.phoneNumber}-${option.id}`}>
              <Stack direction="column">
                <Typography>{option.name}</Typography>
                <Typography variant="caption" color="textSecondary">
                  {option.phoneNumber}
                </Typography>
              </Stack>
            </li>
          );
        }}
        isOptionEqualToValue={(option, value) => {
          return option.id === value.id;
        }}
        onChange={(_event, value) => {
          setSelectedClient(value);
          if (value) {
            if (value.id === "-1") {
              onChange({ name: "", phoneNumber: "" });
              return;
            }
            onChange({
              name: value.name,
              phoneNumber: value.phoneNumber ? value.phoneNumber : "",
              clientId: parseInt(value.id),
            });
          } else {
            onChange({ name: "", phoneNumber: "" });
          }
        }}
        filterOptions={(options, params) =>
          filteredOptions(options, params.inputValue)
        }
        renderInput={(params) => (
          <TextField
            {...params}
            label="Client"
            InputProps={{
              ...params.InputProps,
            }}
          />
        )}
      />
    )
  );
}
