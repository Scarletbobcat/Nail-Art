import { ReactNode } from "react";
import { Stack, Paper, Box, IconButton, Typography } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import InboxIcon from "@mui/icons-material/Inbox";
import { motion, type Variants } from "framer-motion";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface CardListProps<T extends Record<string, any>> {
  data: T[];
  renderPrimary: (item: T) => ReactNode;
  renderSecondary?: (item: T) => ReactNode;
  onEdit?: (item: T) => void;
  onDelete?: (item: T) => void;
  accentColor?: string;
  emptyMessage?: string;
}

const listVariants: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.04 },
  },
};

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 8 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.2, ease: "easeOut" },
  },
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export default function CardList<T extends Record<string, any>>({
  data,
  renderPrimary,
  renderSecondary,
  onEdit,
  onDelete,
  accentColor = "primary.main",
  emptyMessage = "No results found",
}: CardListProps<T>) {
  if (data.length === 0) {
    return (
      <Box sx={{ textAlign: "center", py: 6 }}>
        <InboxIcon sx={{ fontSize: 48, color: "text.secondary", mb: 1, opacity: 0.5 }} />
        <Typography color="text.secondary">{emptyMessage}</Typography>
      </Box>
    );
  }

  return (
    <Stack
      component={motion.div}
      variants={listVariants}
      initial="hidden"
      animate="visible"
      spacing={1.5}
    >
      {data.map((item, index) => (
        <Paper
          component={motion.div}
          variants={itemVariants}
          key={item.id ?? index}
          sx={{
            display: "flex",
            overflow: "hidden",
            transition: "box-shadow 0.2s, transform 0.2s",
            "&:hover": {
              boxShadow: 3,
              transform: "translateY(-1px)",
            },
          }}
        >
          <Box
            sx={{
              width: 4,
              flexShrink: 0,
              bgcolor: accentColor,
              borderRadius: "4px 0 0 4px",
            }}
          />
          <Box
            sx={{
              flex: 1,
              display: "flex",
              alignItems: "flex-start",
              p: 2,
              gap: 1.5,
              minWidth: 0,
            }}
          >
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Box sx={{ fontWeight: 600, fontSize: "0.95rem", color: "text.primary" }}>
                {renderPrimary(item)}
              </Box>
              {renderSecondary && (
                <Box sx={{ color: "text.secondary", fontSize: "0.82rem", mt: 0.5, lineHeight: 1.6 }}>
                  {renderSecondary(item)}
                </Box>
              )}
            </Box>
            {(onEdit || onDelete) && (
              <Stack direction="row" spacing={0.25} sx={{ flexShrink: 0 }}>
                {onEdit && (
                  <IconButton
                    onClick={() => onEdit(item)}
                    sx={{
                      width: 40,
                      height: 40,
                      color: "primary.main",
                      "&:hover": { bgcolor: "primary.main", color: "white" },
                      transition: "all 0.15s",
                    }}
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                )}
                {onDelete && (
                  <IconButton
                    onClick={() => onDelete(item)}
                    sx={{
                      width: 40,
                      height: 40,
                      color: "error.light",
                      "&:hover": { bgcolor: "error.main", color: "white" },
                      transition: "all 0.15s",
                    }}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                )}
              </Stack>
            )}
          </Box>
        </Paper>
      ))}
    </Stack>
  );
}
