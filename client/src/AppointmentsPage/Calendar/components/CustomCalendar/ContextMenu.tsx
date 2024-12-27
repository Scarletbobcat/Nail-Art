import { ListItemIcon, Menu, MenuItem } from "@mui/material";
import React from "react";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import CheckIcon from "@mui/icons-material/Check";
import { Appointment } from "../../../../types";

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
  const onEditClick = (e: React.MouseEvent) => {
    setEdit(true);
    onClose(e);
  };
  const onDeleteClick = (e: React.MouseEvent) => {
    setDelete(true);
    onClose(e);
  };
  const onCheckClick = async (e: React.MouseEvent) => {
    await editShowedUp();
    onClose(e);
    await renderEvents();
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
          <MenuItem onClick={onCheckClick}>
            <ListItemIcon>
              <CheckIcon fontSize="small" />
            </ListItemIcon>
            Check Out
          </MenuItem>
        ) : (
          <MenuItem onClick={onCheckClick}>
            <ListItemIcon>
              <CheckIcon fontSize="small" />
            </ListItemIcon>
            Check In
          </MenuItem>
        )
      ) : null}
    </Menu>
  );
}
