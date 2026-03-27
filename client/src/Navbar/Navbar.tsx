import { Link, useLocation } from "react-router-dom";
import { useState } from "react";
import {
  AppBar,
  Typography,
  Toolbar,
  Menu,
  MenuItem,
  Button,
  Box,
} from "@mui/material";

const navItems = [
  {
    title: "Appointments",
    subMenu: [
      {
        title: "Search",
        url: "/Appointments/Search",
      },
      {
        title: "Calendar",
        url: "/Appointments",
      },
    ],
  },
  { title: "Employees", url: "/Employees" },
  { title: "Services", url: "/Services" },
  { title: "Clients", url: "/Clients" },
];

const authItems = [{ title: "Login", url: "/Login" }];

function Navbar() {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);
  const location = useLocation();

  const isActive = (url: string) => location.pathname === url;
  const isGroupActive = (subMenu: { url: string }[]) =>
    subMenu.some((item) => location.pathname === item.url);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <AppBar
      position="static"
      elevation={0}
      sx={{
        backgroundColor: "background.paper",
        borderBottom: "1px solid",
        borderColor: "divider",
        color: "text.primary",
      }}
    >
      <Toolbar sx={{ maxWidth: 960, mx: "auto", width: "100%" }}>
        <Typography
          variant="h5"
          sx={{
            pr: 3,
            fontWeight: 700,
          }}
        >
          <Link
            to="/"
            style={{
              color: "inherit",
              textDecoration: "none",
            }}
          >
            Nail Art & Spa
          </Link>
        </Typography>
        <Box
          sx={{
            display: { xs: "none", sm: "flex" },
            justifyContent: "space-between",
            flexGrow: 1,
          }}
        >
          <Box sx={{ display: "flex", gap: 0.5 }}>
            {navItems.map((item, index) => {
              if (item.url) {
                const active = isActive(item.url);
                return (
                  <Button
                    key={index}
                    component={Link}
                    to={item.url}
                    sx={{
                      color: active ? "primary.main" : "text.primary",
                      borderBottom: active ? 2 : 0,
                      borderColor: "primary.main",
                      borderRadius: 0,
                      px: 2,
                    }}
                  >
                    {item.title}
                  </Button>
                );
              } else {
                const active = isGroupActive(item.subMenu ?? []);
                return (
                  <div key={index}>
                    <Button
                      aria-controls={open ? "basic-menu" : undefined}
                      aria-haspopup="true"
                      aria-expanded={open ? "true" : undefined}
                      onClick={handleClick}
                      sx={{
                        color: active ? "primary.main" : "text.primary",
                        borderBottom: active ? 2 : 0,
                        borderColor: "primary.main",
                        borderRadius: 0,
                        px: 2,
                      }}
                    >
                      {item.title}
                    </Button>
                    <Menu
                      id="basic-menu"
                      anchorEl={anchorEl}
                      open={Boolean(anchorEl)}
                      onClose={handleClose}
                      sx={{
                        "& .MuiMenu-paper": {
                          width: anchorEl
                            ? anchorEl.getBoundingClientRect().width
                            : "auto",
                        },
                      }}
                    >
                      {item.subMenu?.map((subItem, subIndex) => (
                        <MenuItem
                          onClick={handleClose}
                          key={subIndex}
                          selected={isActive(subItem.url)}
                        >
                          <Link
                            to={subItem.url}
                            style={{
                              display: "block",
                              width: "100%",
                              height: "100%",
                              textDecoration: "none",
                              color: "inherit",
                            }}
                          >
                            {subItem.title}
                          </Link>
                        </MenuItem>
                      ))}
                    </Menu>
                  </div>
                );
              }
            })}
          </Box>
          <Box sx={{ display: "flex" }}>
            {authItems.map((item, index) => {
              if (!localStorage.getItem("token")) {
                return (
                  <Button
                    key={index}
                    component={Link}
                    to={item.url}
                    variant="outlined"
                    size="small"
                  >
                    {item.title}
                  </Button>
                );
              }
            })}
          </Box>
        </Box>
      </Toolbar>
    </AppBar>
  );
}

export default Navbar;
