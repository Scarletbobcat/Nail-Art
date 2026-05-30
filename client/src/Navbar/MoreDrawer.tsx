import {
  SwipeableDrawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Box,
} from "@mui/material";
import { Link } from "react-router-dom";
import BadgeIcon from "@mui/icons-material/Badge";
import ContentCutIcon from "@mui/icons-material/ContentCut";
import SettingsIcon from "@mui/icons-material/Settings";
import LoginIcon from "@mui/icons-material/Login";
import LogoutIcon from "@mui/icons-material/Logout";
import { logout } from "../api/auth/auth";
import { useMe } from "../hooks/useMe";

const baseItems = [
  { title: "Employees", url: "/Employees", icon: <BadgeIcon /> },
  { title: "Services", url: "/Services", icon: <ContentCutIcon /> },
];

export default function MoreDrawer({
  open,
  onOpen,
  onClose,
}: {
  open: boolean;
  onOpen: () => void;
  onClose: () => void;
}) {
  const isLoggedIn = Boolean(localStorage.getItem("token"));
  const { data: me } = useMe();
  // Settings is owner-only; staff never see the entry.
  const moreItems =
    me?.user.role === "owner"
      ? [...baseItems, { title: "Settings", url: "/Settings", icon: <SettingsIcon /> }]
      : baseItems;

  return (
    <SwipeableDrawer
      anchor="bottom"
      open={open}
      onOpen={onOpen}
      onClose={onClose}
      disableSwipeToOpen
      sx={{
        "& .MuiDrawer-paper": {
          borderTopLeftRadius: 16,
          borderTopRightRadius: 16,
          maxHeight: "60vh",
        },
      }}
    >
      <Box sx={{ display: "flex", justifyContent: "center", py: 1 }}>
        <Box
          sx={{
            width: 32,
            height: 4,
            borderRadius: 2,
            bgcolor: "grey.300",
          }}
        />
      </Box>
      <List>
        {moreItems.map((item) => (
          <ListItemButton
            key={item.title}
            component={Link}
            to={item.url}
            onClick={onClose}
          >
            <ListItemIcon>{item.icon}</ListItemIcon>
            <ListItemText primary={item.title} />
          </ListItemButton>
        ))}
        {isLoggedIn ? (
          <ListItemButton onClick={() => { onClose(); logout(); }}>
            <ListItemIcon><LogoutIcon /></ListItemIcon>
            <ListItemText primary="Logout" />
          </ListItemButton>
        ) : (
          <ListItemButton component={Link} to="/Login" onClick={onClose}>
            <ListItemIcon><LoginIcon /></ListItemIcon>
            <ListItemText primary="Login" />
          </ListItemButton>
        )}
      </List>
    </SwipeableDrawer>
  );
}
