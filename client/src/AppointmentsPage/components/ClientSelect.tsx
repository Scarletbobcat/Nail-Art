import {
  Autocomplete,
  TextField,
  CircularProgress,
  Typography,
  Stack,
} from "@mui/material";
import { useState } from "react";
import { Client } from "../../types/Client";
import { getClients } from "../../api/clients";

export default function ClientSelect({
  onChange,
  show,
}: {
  onChange: (client: {
    name: string;
    phoneNumber: string;
    clientId?: number | undefined;
  }) => void;
  show?: boolean;
}) {
  const [options, setOptions] = useState<Client[]>([]);
  const [selectedClient, setSelectedClient] = useState<Client | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleOpen = async () => {
    setIsLoading(true);
    const clients = await getClients();
    setOptions(clients);
    setIsLoading(false);
  };

  const filteredOptions = (options: Client[], inputValue: string) => {
    const filtered = options.filter((option) => {
      return (
        option.name.toLowerCase().includes(inputValue.toLowerCase()) ||
        option.phoneNumber.includes(inputValue)
      );
    });

    return filtered;
  };

  return (
    show && (
      <Autocomplete
        id="client-select"
        value={selectedClient}
        options={options}
        getOptionLabel={(option) => {
          return option.name;
        }}
        getOptionKey={(option: Client) => option.phoneNumber}
        renderOption={(props, option: Client) => {
          return (
            <li {...props} key={option.phoneNumber}>
              <Stack direction="column">
                <Typography>{option.name}</Typography>
                <Typography variant="caption" color="textSecondary">
                  {option.phoneNumber}
                </Typography>
              </Stack>
            </li>
          );
        }}
        style={{ width: 300 }}
        loading={isLoading}
        onOpen={handleOpen}
        isOptionEqualToValue={(option, value) => {
          return option.id === value.id;
        }}
        onChange={(_event, value) => {
          setSelectedClient(value);
          if (value) {
            onChange({
              name: value.name,
              phoneNumber: value.phoneNumber,
              clientId: value.id,
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
              endAdornment: (
                <>
                  {isLoading && <CircularProgress color="inherit" size={20} />}
                  {params.InputProps.endAdornment}
                </>
              ),
            }}
          />
        )}
      />
    )
  );
}
