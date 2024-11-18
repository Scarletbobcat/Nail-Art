import { Link } from "react-router-dom";
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
];

const authItems = [
  { title: "Login", url: "/Login" },
  { title: "Register", url: "/Register" },
];

function Navbar() {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography
          variant="h5"
          style={{
            paddingRight: "20px",
            textDecoration: "none",
            color: "inherit",
          }}
        >
          <Link
            to="/"
            style={{
              color: "inherit",
              textDecoration: "none",
            }}
          >
            Nail Art & Spa LLC.
          </Link>
        </Typography>
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            flexGrow: 1,
          }}
        >
          <Box
            sx={{
              display: "inline-flex",
            }}
          >
            {navItems.map((item, index) => {
              if (item.url) {
                return (
                  <Button key={index} color="inherit">
                    <Link
                      to={item.url}
                      style={{
                        display: "block",
                        width: "100%",
                        height: "100%",
                        textDecoration: "none",
                        color: "inherit",
                      }}
                    >
                      {item.title}
                    </Link>
                  </Button>
                );
              } else {
                return (
                  <div key={index}>
                    <Button
                      color="inherit"
                      aria-controls={open ? "basic-menu" : undefined}
                      aria-haspopup="true"
                      aria-expanded={open ? "true" : undefined}
                      onClick={handleClick}
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
                            : "auto", // Set width to button width
                        },
                      }}
                    >
                      {item.subMenu?.map((subItem, index) => {
                        return (
                          <MenuItem onClick={handleClose} key={index}>
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
                        );
                      })}
                    </Menu>
                  </div>
                );
              }
            })}
          </Box>
          <Box
            sx={{
              display: "inline-flex",
            }}
          >
            {authItems.map((item, index) => {
              return (
                <Button key={index} color="inherit">
                  <Link
                    to={item.url}
                    style={{
                      display: "block",
                      width: "100%",
                      height: "100%",
                      textDecoration: "none",
                      color: "inherit",
                    }}
                  >
                    {item.title}
                  </Link>
                </Button>
              );
            })}
          </Box>
        </Box>
      </Toolbar>
    </AppBar>
  );
}

export default Navbar;
