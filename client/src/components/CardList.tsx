import { ReactNode, useState, useRef, useEffect } from "react";
import {
  Stack,
  Paper,
  Box,
  IconButton,
  Typography,
  Tooltip,
  Popover,
  TextField,
  Button,
} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import InboxIcon from "@mui/icons-material/Inbox";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import { motion, type Variants } from "framer-motion";

export interface CardListOrdering {
  positionOffset: number;
  totalCount: number;
  onMoveUp: (index: number) => void;
  onMoveDown: (index: number) => void;
  onMoveTo: (index: number, newPosition: number) => void;
  busyIndex?: number | null;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface CardListProps<T extends Record<string, any>> {
  data: T[];
  renderPrimary: (item: T) => ReactNode;
  renderSecondary?: (item: T) => ReactNode;
  onEdit?: (item: T) => void;
  onDelete?: (item: T) => void;
  accentColor?: string;
  emptyMessage?: string;
  ordering?: CardListOrdering;
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
  ordering,
}: CardListProps<T>) {
  const [jumpAnchor, setJumpAnchor] = useState<HTMLElement | null>(null);
  const [jumpIndex, setJumpIndex] = useState<number | null>(null);
  const [jumpValue, setJumpValue] = useState("");
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (jumpAnchor) {
      const t = setTimeout(() => inputRef.current?.select(), 50);
      return () => clearTimeout(t);
    }
  }, [jumpAnchor]);

  if (data.length === 0) {
    return (
      <Box sx={{ textAlign: "center", py: 6 }}>
        <InboxIcon sx={{ fontSize: 48, color: "text.secondary", mb: 1, opacity: 0.5 }} />
        <Typography color="text.secondary">{emptyMessage}</Typography>
      </Box>
    );
  }

  const openJump = (event: React.MouseEvent<HTMLElement>, index: number, position: number) => {
    setJumpAnchor(event.currentTarget);
    setJumpIndex(index);
    setJumpValue(String(position));
  };

  const closeJump = () => {
    setJumpAnchor(null);
    setJumpIndex(null);
    setJumpValue("");
  };

  const submitJump = () => {
    if (jumpIndex === null || !ordering) return;
    const parsed = parseInt(jumpValue, 10);
    if (Number.isNaN(parsed)) return closeJump();
    const clamped = Math.max(1, Math.min(ordering.totalCount, parsed));
    const currentPosition = ordering.positionOffset + jumpIndex + 1;
    if (clamped !== currentPosition) {
      ordering.onMoveTo(jumpIndex, clamped);
    }
    closeJump();
  };

  return (
    <>
      <Stack
        component={motion.div}
        variants={listVariants}
        initial="hidden"
        animate="visible"
        spacing={1.5}
      >
        {data.map((item, index) => {
          const position = ordering ? ordering.positionOffset + index + 1 : null;
          const canMoveUp = ordering ? position! > 1 : false;
          const canMoveDown = ordering ? position! < ordering.totalCount : false;
          const isBusy = ordering?.busyIndex === index;

          return (
            <Paper
              component={motion.div}
              variants={itemVariants}
              key={item.id ?? index}
              sx={{
                display: "flex",
                overflow: "hidden",
                transition: "box-shadow 0.2s, transform 0.2s, opacity 0.2s",
                opacity: isBusy ? 0.55 : 1,
                "&:hover": {
                  boxShadow: 3,
                  transform: "translateY(-1px)",
                },
                "&:hover .position-chevrons": {
                  opacity: 1,
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
                  alignItems: "center",
                  p: 2,
                  gap: 1.5,
                  minWidth: 0,
                }}
              >
                {ordering && position !== null && (
                  <Stack
                    direction="row"
                    alignItems="center"
                    spacing={0.25}
                    sx={{ flexShrink: 0 }}
                  >
                    <Tooltip title="Click to jump to position" placement="top">
                      <Box
                        component="button"
                        onClick={(e) => openJump(e, index, position)}
                        disabled={isBusy}
                        sx={{
                          all: "unset",
                          cursor: isBusy ? "wait" : "pointer",
                          minWidth: 36,
                          height: 36,
                          px: 1,
                          display: "inline-flex",
                          alignItems: "center",
                          justifyContent: "center",
                          borderRadius: "10px",
                          border: "1px solid",
                          borderColor: "divider",
                          bgcolor: "background.default",
                          fontFamily:
                            '"SF Mono", "JetBrains Mono", "Roboto Mono", ui-monospace, monospace',
                          fontSize: "0.85rem",
                          fontWeight: 600,
                          fontVariantNumeric: "tabular-nums",
                          color: "text.primary",
                          letterSpacing: "0.02em",
                          transition: "all 0.15s",
                          "&:hover:not(:disabled)": {
                            borderColor: accentColor,
                            color: accentColor,
                            transform: "translateY(-1px)",
                          },
                          "&:focus-visible": {
                            outline: "2px solid",
                            outlineColor: accentColor,
                            outlineOffset: 2,
                          },
                        }}
                      >
                        {position}
                      </Box>
                    </Tooltip>
                    <Stack
                      className="position-chevrons"
                      direction="column"
                      sx={{
                        opacity: { xs: 1, sm: 0 },
                        transition: "opacity 0.15s",
                      }}
                    >
                      <IconButton
                        size="small"
                        disabled={!canMoveUp || isBusy}
                        onClick={() => ordering.onMoveUp(index)}
                        aria-label="Move up"
                        sx={{
                          width: 22,
                          height: 18,
                          borderRadius: 1,
                          color: "text.secondary",
                          "&:hover": { color: accentColor, bgcolor: "action.hover" },
                        }}
                      >
                        <KeyboardArrowUpIcon sx={{ fontSize: 16 }} />
                      </IconButton>
                      <IconButton
                        size="small"
                        disabled={!canMoveDown || isBusy}
                        onClick={() => ordering.onMoveDown(index)}
                        aria-label="Move down"
                        sx={{
                          width: 22,
                          height: 18,
                          borderRadius: 1,
                          color: "text.secondary",
                          "&:hover": { color: accentColor, bgcolor: "action.hover" },
                        }}
                      >
                        <KeyboardArrowDownIcon sx={{ fontSize: 16 }} />
                      </IconButton>
                    </Stack>
                  </Stack>
                )}
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
          );
        })}
      </Stack>

      <Popover
        open={Boolean(jumpAnchor)}
        anchorEl={jumpAnchor}
        onClose={closeJump}
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
        transformOrigin={{ vertical: "top", horizontal: "left" }}
        slotProps={{
          paper: {
            sx: {
              mt: 0.5,
              p: 1.5,
              borderRadius: 2,
              boxShadow: 8,
              minWidth: 240,
            },
          },
        }}
      >
        <Stack spacing={1}>
          <Typography variant="caption" sx={{ color: "text.secondary", fontWeight: 600, letterSpacing: "0.04em", textTransform: "uppercase" }}>
            Move to position
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField
              inputRef={inputRef}
              size="small"
              type="number"
              value={jumpValue}
              onChange={(e) => setJumpValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  submitJump();
                } else if (e.key === "Escape") {
                  closeJump();
                }
              }}
              inputProps={{
                min: 1,
                max: ordering?.totalCount ?? undefined,
                style: { fontVariantNumeric: "tabular-nums" },
              }}
              sx={{ flex: 1 }}
              autoFocus
            />
            <Typography variant="body2" sx={{ color: "text.secondary", whiteSpace: "nowrap" }}>
              of {ordering?.totalCount ?? 0}
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button size="small" onClick={closeJump}>
              Cancel
            </Button>
            <Button size="small" variant="contained" onClick={submitJump}>
              Move
            </Button>
          </Stack>
        </Stack>
      </Popover>
    </>
  );
}
