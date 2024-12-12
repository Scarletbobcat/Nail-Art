import { Stack, List, ListItem, ListItemText } from "@mui/material";
import dayjs from "dayjs";

export default function AppointmentCalendar({
  startDate,
}: {
  startDate: dayjs.Dayjs;
}) {
  return (
    <div>
      <Stack>
        <List>
          <ListItem>
            <ListItemText
              primary={
                startDate.format("YYYY-MM-DD") ?? dayjs().format("YYYY-MM-DD")
              }
            />
          </ListItem>
        </List>
      </Stack>
    </div>
  );
}
