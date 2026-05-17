import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { projectService } from '../services/projectService';
import { ProjectStatus } from '../types';

export function useGenerationPolling(projectId: string, status: ProjectStatus) {
  const queryClient = useQueryClient();
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (status !== 'PENDING' && status !== 'PROCESSING') {
      if (intervalRef.current) clearInterval(intervalRef.current);
      return;
    }

    intervalRef.current = setInterval(async () => {
      const gen = await projectService.getGenerationStatus(projectId);

      if (gen.status === 'DONE' || gen.status === 'FAILED') {
        await queryClient.invalidateQueries({ queryKey: ['project', projectId] });
        await queryClient.invalidateQueries({ queryKey: ['projects'] });
        if (intervalRef.current) clearInterval(intervalRef.current);
      }
    }, 3000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [projectId, status, queryClient]);
}
