import { ListItemIcon, Menu, MenuItem } from "@mui/material";
import React from "react";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";

export default function ContextMenu({
  onClose,
  setEdit,
  setDelete,
  open,
  anchorPosition,
}: {
  onClose: (e: React.MouseEvent) => void;
  setEdit: (editOpen: boolean) => void;
  setDelete: (deleteOpen: boolean) => void;
  open: boolean;
  anchorPosition: { top: number; left: number } | undefined;
}) {
  const onEditClick = (e: React.MouseEvent) => {
    setEdit(true);
    onClose(e);
  };
  const onDeleteClick = (e: React.MouseEvent) => {
    setDelete(true);
    onClose(e);
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
    </Menu>
  );
}
