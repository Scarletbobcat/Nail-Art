import { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
  BottomNavigation,
  BottomNavigationAction,
  Paper,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import CalendarTodayIcon from "@mui/icons-material/CalendarToday";
import SearchIcon from "@mui/icons-material/Search";
import PeopleIcon from "@mui/icons-material/People";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import MoreDrawer from "./MoreDrawer";

const tabs = [
  { label: "Calendar", icon: <CalendarTodayIcon />, path: "/Appointments" },
  { label: "Search", icon: <SearchIcon />, path: "/Appointments/Search" },
  { label: "Clients", icon: <PeopleIcon />, path: "/Clients" },
];

export default function MobileBottomNav() {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const navigate = useNavigate();
  const location = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);

  if (!isMobile) return null;

  const currentTab = tabs.findIndex((tab) => tab.path === location.pathname);

  return (
    <>
      <Paper
        sx={{
          position: "fixed",
          bottom: 0,
          left: 0,
          right: 0,
          zIndex: (t) => t.zIndex.appBar,
          borderTop: "1px solid",
          borderColor: "divider",
          borderBottom: "none",
          borderLeft: "none",
          borderRight: "none",
        }}
        elevation={3}
      >
        <BottomNavigation
          value={currentTab === -1 ? false : currentTab}
          onChange={(_, newValue) => {
            if (newValue < tabs.length) {
              navigate(tabs[newValue].path);
            }
          }}
          showLabels={false}
          sx={{
            height: 56,
            "& .MuiBottomNavigationAction-root": {
              color: "text.secondary",
              minWidth: 0,
            },
            "& .Mui-selected": {
              color: "primary.main",
            },
          }}
        >
          {tabs.map((tab) => (
            <BottomNavigationAction
              key={tab.label}
              label={tab.label}
              icon={tab.icon}
              showLabel={currentTab === tabs.indexOf(tab)}
            />
          ))}
          <BottomNavigationAction
            label="More"
            icon={<MoreHorizIcon />}
            showLabel={moreOpen}
            onClick={() => setMoreOpen(true)}
          />
        </BottomNavigation>
      </Paper>
      <MoreDrawer
        open={moreOpen}
        onOpen={() => setMoreOpen(true)}
        onClose={() => setMoreOpen(false)}
      />
    </>
  );
}
