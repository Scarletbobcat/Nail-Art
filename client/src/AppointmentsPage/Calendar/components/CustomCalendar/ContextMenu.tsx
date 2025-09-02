import {
  ListItemIcon,
  Menu,
  MenuItem,
  CircularProgress,
  Stack,
} from "@mui/material";
import React from "react";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import CheckIcon from "@mui/icons-material/Check";
import EventIcon from "@mui/icons-material/Event";
import { Appointment } from "../../../../types";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function ContextMenu({
  onClose,
  setEdit,
  setDelete,
  open,
  anchorPosition,
  editShowedUp,
  renderEvents,
  appointment,
}: {
  onClose: (e: React.MouseEvent) => void;
  setEdit: (editOpen: boolean) => void;
  setDelete: (deleteOpen: boolean) => void;
  open: boolean;
  anchorPosition: { top: number; left: number } | undefined;
  editShowedUp: () => void;
  appointment: Appointment;
  renderEvents: () => void;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const nav = useNavigate();
  const onEditClick = (e: React.MouseEvent) => {
    setEdit(true);
    onClose(e);
  };
  const onDeleteClick = (e: React.MouseEvent) => {
    setDelete(true);
    onClose(e);
  };
  const onCheckClick = async (e: React.MouseEvent) => {
    setIsLoading(true);
    await editShowedUp();
    await renderEvents();
    onClose(e);
    setIsLoading(false);
  };
  const onFutureApptClick = async (e: React.MouseEvent) => {
    setIsLoading(true);
    onClose(e);
    nav(`/Appointments/Search?pn=${appointment.phoneNumber}`);
    setIsLoading(false);
  };

  return (
    <Menu
      open={open}
      onClose={onClose}
      anchorReference="anchorPosition"
      anchorPosition={anchorPosition}
    >
      <MenuItem onClick={onEditClick} autoFocus={false}>
        <ListItemIcon>
          <EditIcon fontSize="small" />
        </ListItemIcon>
        Edit
      </MenuItem>
      <MenuItem onClick={onDeleteClick}>
        <ListItemIcon>
          <DeleteIcon fontSize="small" />
        </ListItemIcon>
        Delete
      </MenuItem>
      {/* if the event is not a service appointment, do not render the check in/out option */}
      {appointment && !appointment.services.includes(3) ? (
        // otherwise, depending on the appointment showedUp status, render the check in/out option
        appointment && appointment.showedUp ? (
          <MenuItem onClick={onCheckClick} disabled={isLoading}>
            <Stack direction="row" spacing={2}>
              <Stack direction="row">
                <ListItemIcon>
                  <CheckIcon fontSize="small" />
                </ListItemIcon>
                Check Out
              </Stack>
              <ListItemIcon>
                {isLoading ? <CircularProgress size={20} /> : null}
              </ListItemIcon>
            </Stack>
          </MenuItem>
        ) : (
          <MenuItem onClick={onCheckClick} disabled={isLoading}>
            <Stack direction="row" spacing={2}>
              <Stack direction="row">
                <ListItemIcon>
                  <CheckIcon fontSize="small" />
                </ListItemIcon>
                Check In
              </Stack>
              <ListItemIcon>
                {isLoading ? <CircularProgress size={20} /> : null}
              </ListItemIcon>
            </Stack>
          </MenuItem>
        )
      ) : null}
      <MenuItem onClick={onFutureApptClick}>
        <ListItemIcon>
          <EventIcon fontSize="small" />
        </ListItemIcon>
        All Appointments
      </MenuItem>
    </Menu>
  );
}
